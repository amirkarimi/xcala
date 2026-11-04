package xcala.play.services

import xcala.play.models.SentryExtendedBase

import play.api.Configuration
import play.api.data.Form
import play.api.data.FormBinding
import play.api.data.FormError
import play.api.mvc.AnyContent
import play.api.mvc.Request

import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import com.nappin.play.recaptcha.RecaptchaSettings
import com.nappin.play.recaptcha.RecaptchaVerifier
import io.sentry.Hint

@Singleton
class RecaptchaService @Inject() (
    verifier     : RecaptchaVerifier,
    configuration: Configuration,
    settings     : RecaptchaSettings
) {

  val isRecaptchaEnabled: Boolean = configuration.get[Boolean]("isRecaptchaEnabled")

  def bindActiveFormBinder[T](form: Form[T])(implicit
      request           : Request[AnyContent],
      ec                : ExecutionContext,
      binding           : FormBinding,
      sentryExtendedBase: SentryExtendedBase
  ): Future[Form[T]] =
    isRecaptchaEnabled match {
      case true =>
        verifier.bindFromRequestAndVerify(form).map {
          form =>
            form.errors.find {
              case FormError(key, _, _) if key.contains("recaptcha") => true
              case _                                                 => false
            }.foreach {
              formError =>
                val hint = new Hint
                hint.set("request timeout", s"${settings.requestTimeoutMs} ms")
                sentryExtendedBase.captureExceptionWithHint(
                  new Throwable(s"Recaptcha error: $formError"),
                  hint
                )
            }

            form
        }

      case false =>
        Future.successful(form.bindFromRequest())
    }

}

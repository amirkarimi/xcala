package xcala.play.controllers

import xcala.play.extensions.FormHelper._

import play.api.data.Form
import play.api.data.FormBinding
import play.api.i18n.Messages
import play.api.mvc.Request

import scala.concurrent.Future

trait WithFormBinding {
  type RequestType[A] <: Request[A]

  protected def bindForm[B](
      form: Form[B]
  )(implicit request: RequestType[_], formBinding: FormBinding, messages: Messages): Future[Form[B]] = {
    Future.successful(form.bindFromRequest().fixLanguageChars)
  }

}

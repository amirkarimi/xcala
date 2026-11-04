package xcala.play.models

import play.api.mvc.RequestHeader

import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.protocol.SentryId

trait SentryExtendedBase {

  protected def captureExceptionCore(
      throwable   : Throwable,
      maybeRequest: Option[RequestHeader],
      maybeHint   : Option[Hint]
  ): SentryId

  @SuppressWarnings(Array("NullAssignment"))
  def captureException(e: Throwable)(implicit nullableRequest: RequestHeader = null): SentryId = {
    val maybeRequest: Option[RequestHeader] = Option(nullableRequest)
    captureExceptionCore(e, maybeRequest, None)
  }

  @SuppressWarnings(Array("NullAssignment"))
  def captureExceptionWithHint(e: Throwable, hint: Hint)(implicit
      nullableRequest: RequestHeader = null
  ): SentryId = {
    val maybeRequest: Option[RequestHeader] = Option(nullableRequest)
    captureExceptionCore(e, maybeRequest, Some(hint))
  }

  def captureMessage(message: String): Unit =
    Sentry.captureMessage(message)

}

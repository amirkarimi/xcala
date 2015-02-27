package xcala.play.models

import play.api.i18n._
import play.api.mvc._

case class LocalizedRequest[A](val lang: Lang, request: Request[A]) extends WrappedRequest[A](request)
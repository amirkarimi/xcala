package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc._

import scala.concurrent.Future

trait WithComposableActions {
  type RequestType <: Request[_]

  def Action[A](block: RequestType => Future[Result])(implicit lang: Lang): play.api.mvc.Action[AnyContent]
}

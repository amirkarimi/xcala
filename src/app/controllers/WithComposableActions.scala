package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc._

import scala.concurrent.Future

trait WithComposableActions {
  type RequestType <: Request[_]

  def Action[A](lang: Lang)(block: RequestType => Future[Result]): play.api.mvc.Action[AnyContent] = {
    Action(block)(lang)
  }

  def Action[A](block: RequestType => Future[Result])(implicit lang: Lang): play.api.mvc.Action[AnyContent]
}

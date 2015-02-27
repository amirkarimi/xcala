package xcala.play.controllers

import scala.concurrent.Future

import play.api.i18n.Lang
import play.api.mvc._
import play.api.mvc.BodyParsers.parse

trait WithComposableActions {
  type RequestType[A] <: Request[A]

  def action(block: RequestType[AnyContent] => Future[Result])(implicit lang: Lang): Action[AnyContent] = {
    action(parse.anyContent)(block)
  }

  def action(lang: Lang)(block: RequestType[AnyContent] => Future[Result]): Action[AnyContent] = {
    action(parse.anyContent, lang)(block)
  }

  def action[A](bodyParser: BodyParser[A])(block: RequestType[A] => Future[Result])(implicit lang: Lang): Action[A] = {
    action(bodyParser, lang)(block)
  }
  
  def action[A](bodyParser: BodyParser[A], lang: Lang)(block: RequestType[A] => Future[Result]): Action[A]
}

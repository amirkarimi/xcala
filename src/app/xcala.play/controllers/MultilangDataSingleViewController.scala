package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc._
import play.api.Play.current
import reactivemongo.bson._
import xcala.play.models._
import xcala.play.services._

import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait MultilangDataSingleViewController[A <: WithLang] extends Results with WithComposableActions with WithExecutionContext {
  
  protected val readService: DataReadService[A]
  
  def singleView(model: A)(implicit request: RequestType[_]): Future[Result]
  
  def view(id: BSONObjectID): EssentialAction = action.async { implicit request =>
    readService.findById(id) flatMap {
      case None => Future.successful(NotFound)
      case Some(model) =>
        singleView(model).map(_.withLang(Lang(model.lang)))
    }
  }
}

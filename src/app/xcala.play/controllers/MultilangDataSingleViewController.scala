package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc._
import reactivemongo.bson._
import xcala.play.models._
import xcala.play.services._

import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait MultilangDataSingleViewController[A <: WithLang] extends Results with WithComposableActions with WithExecutionContext {
  
  protected val readService: DataReadService[A]
  
  def singleView(model: A)(implicit request: RequestType[_], lang: Lang): Future[Result]
  
  def view(id: BSONObjectID): EssentialAction = play.api.mvc.Action.async { implicit request =>
    readService.findById(id) flatMap {
      case None => Future.successful(NotFound)
      case Some(model) =>
        val actionWithLang = action(Lang(model.lang)) { implicit request => implicit lang =>
          singleView(model)
        }
      
        actionWithLang(request)
    }
  }
}

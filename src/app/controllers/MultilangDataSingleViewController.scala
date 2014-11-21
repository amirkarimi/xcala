package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc._
import reactivemongo.bson._
import xcala.play.models._
import xcala.play.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MultilangDataSingleViewController[A <: WithLang] extends Results with WithComposableActions {
  
  protected val readService: DataReadService[A]
  
  def singleView(model: A)(implicit request: RequestType, lang: Lang): Future[Result]
  
  def view(id: BSONObjectID): EssentialAction = play.api.mvc.Action.async { implicit request =>
    readService.findById(id) flatMap {
      case None => Future.successful(NotFound)
      case Some(model) =>
        implicit val lang = Lang(model.lang)
        ContextResult(lang) { implicit request =>
          singleView(model)
        }
    }
  }

  def ContextResult(lang: Lang)(block: RequestType => Future[Result])(implicit request: Request[_]): Future[Result]
}

package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc.Result
import reactivemongo.bson.BSONObjectID
import xcala.play.services.DataReadService

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait DataSingleViewController[A] extends WithComposableActions with WithoutImplicitLang {

  protected val readService: DataReadService[A]

  def singleView(model: A)(implicit request: RequestType, lang: Lang): Future[Result]

  def view(lang: Lang, id: BSONObjectID) = Action(lang) { implicit request =>
    implicit val implicitLang = lang

    readService.findById(id) flatMap {
      case None => Future.successful(NotFound)
      case Some(model) => singleView(model)
    }
  }
}
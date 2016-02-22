package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc.{ Result, Results }
import reactivemongo.bson.BSONObjectID
import xcala.play.services.DataReadService
import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait DataSingleViewController[A] extends Results with WithComposableActions with WithExecutionContext {

  protected val readService: DataReadService[A]

  def singleView(model: A)(implicit request: RequestType[_]): Future[Result]

  def view(lang: Lang, id: BSONObjectID) = action(lang) { implicit request =>
    readService.findById(id) flatMap {
      case None => Future.successful(NotFound)
      case Some(model) => singleView(model)
    }
  }
}
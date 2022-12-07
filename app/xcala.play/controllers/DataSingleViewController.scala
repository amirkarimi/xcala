package xcala.play.controllers

import xcala.play.services.DataReadService
import xcala.play.utils.WithExecutionContext

import play.api.mvc.{Action, AnyContent, Result, Results}

import scala.concurrent.Future

import reactivemongo.api.bson.BSONObjectID

trait DataSingleViewController[A] extends Results with WithComposableActions with WithExecutionContext {

  protected val readService: DataReadService[A]

  def singleView(model: A)(implicit request: RequestType[_]): Future[Result]

  def view(id: BSONObjectID): Action[AnyContent] = action.async { implicit request =>
    readService.findById(id).flatMap {
      case None        => Future.successful(NotFound)
      case Some(model) => singleView(model)
    }
  }

}

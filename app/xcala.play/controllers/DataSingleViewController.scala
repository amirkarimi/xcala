package xcala.play.controllers

import play.api.mvc.Result
import play.api.mvc.Results
import reactivemongo.api.bson.BSONObjectID
import xcala.play.services.DataReadService
import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait DataSingleViewController[A] extends Results with WithComposableActions with WithExecutionContext {

  protected val readService: DataReadService[A]

  def singleView(model: A)(implicit request: RequestType[_]): Future[Result]

  def view(id: BSONObjectID) = action.async { implicit request =>
    readService.findById(id).flatMap {
      case None        => Future.successful(NotFound)
      case Some(model) => singleView(model)
    }
  }

}

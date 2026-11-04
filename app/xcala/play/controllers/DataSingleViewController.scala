package xcala.play.controllers

import xcala.play.models.DocumentWithId
import xcala.play.services.DataReadSimpleService
import xcala.play.utils.WithExecutionContext

import play.api.mvc.{Action, AnyContent, Result, Results}

import scala.concurrent.Future

import reactivemongo.api.bson.BSONObjectID

trait DataSingleViewController[Doc <: DocumentWithId, Model]
    extends Results
    with WithComposableActions
    with WithExecutionContext {

  protected val readService: DataReadSimpleService[Doc, Model]

  def singleView(model: Model)(implicit request: RequestType[_]): Future[Result]

  def view(id: BSONObjectID): Action[AnyContent] = action.async { implicit request =>
    readService.findById(id).flatMap {
      case None        => Future.successful(NotFound)
      case Some(model) => singleView(model)
    }
  }

}

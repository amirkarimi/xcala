package xcala.play.controllers

import xcala.play.models.DocumentWithId
import xcala.play.models.Paginated
import xcala.play.models.QueryOptions
import xcala.play.services._
import xcala.play.utils.WithExecutionContext

import play.api.i18n.MessagesProvider
import play.api.mvc._

import scala.concurrent.Future

trait DataReadController[Doc <: DocumentWithId, Model]
    extends InjectedController
    with WithComposableActions
    with WithExecutionContext {
  implicit val messagesProvider: MessagesProvider

  protected def readService: DataReadService[Doc]

  def indexView(paginated: Paginated[Model])(implicit request: RequestType[_]): Future[Result]

  def indexResultView(paginated: Paginated[Model])(implicit request: RequestType[_]): Future[Result]

  def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[Model]]

  def index: Action[AnyContent] = action.async { implicit request =>
    val queryOptions = QueryOptions.getFromRequest()

    getPaginatedData(queryOptions).flatMap { paginated =>
      request.headers.get("X-Requested-With") match {
        case Some(_) => indexResultView(paginated)
        case None    => indexView(paginated)
      }
    }
  }

}

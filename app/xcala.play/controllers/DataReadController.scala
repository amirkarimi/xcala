package xcala.play.controllers

import xcala.play.models.Paginated
import xcala.play.models.QueryOptions
import xcala.play.services._
import xcala.play.utils.WithExecutionContext

import play.api.mvc._

import scala.concurrent.Future

import reactivemongo.api.bson._

trait DataReadController[A] extends InjectedController with WithComposableActions with WithExecutionContext {

  protected def readService: DataReadService[A]

  def indexView(paginated: Paginated[A])(implicit request: RequestType[_]): Future[Result]

  def indexResultView(paginated: Paginated[A])(implicit request: RequestType[_]): Future[Result]

  def getPaginatedData(
      queryOptions: QueryOptions
  )(implicit @annotation.nowarn request: RequestType[_]): Future[Paginated[A]] = {
    readService.find(BSONDocument(), queryOptions).map { dataWithTotalCount =>
      Paginated(
        dataWithTotalCount.data,
        dataWithTotalCount.totalCount,
        queryOptions
      )
    }
  }

  def index: Action[AnyContent] = action.async { implicit request =>
    val queryOptions = QueryOptions.getFromRequest

    getPaginatedData(queryOptions).flatMap { paginated =>
      request.headers.get("X-Requested-With") match {
        case Some(_) => indexResultView(paginated)
        case None    => indexView(paginated)
      }
    }
  }

}

package xcala.play.controllers

import xcala.play.models.{Paginated, QueryOptions}
import play.api.i18n.Lang
import play.api.mvc._
import reactivemongo.bson._
import xcala.play.services._
import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait DataReadController[A] extends Controller with WithComposableActions with WithExecutionContext {

  protected def readService: DataReadService[A]

  def indexView(paginated: Paginated[A])(implicit request: RequestType[_]): Future[Result]

  def indexResultView(paginated: Paginated[A])(implicit request: RequestType[_]): Future[Result]

  def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    readService.find(BSONDocument(), queryOptions).map { dataWithTotalCount =>
      Paginated(
        dataWithTotalCount.data,
        dataWithTotalCount.totalCount,
        queryOptions)
    }
  }

  def index = action.async { implicit request =>
    val queryOptions = QueryOptions.getFromRequest

    getPaginatedData(queryOptions).flatMap { paginated =>
      request.headers.get("X-Requested-With") match {
        case Some(_) => indexResultView(paginated)
        case None => indexView(paginated)
      }
    }
  }
}

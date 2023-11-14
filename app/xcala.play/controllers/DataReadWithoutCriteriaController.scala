package xcala.play.controllers

import xcala.play.controllers.DataReadController
import xcala.play.models.DocumentWithId
import xcala.play.models.Paginated
import xcala.play.models.QueryOptions
import xcala.play.services.DataReadSimpleService
import xcala.play.utils.WithExecutionContext

import play.api.mvc.InjectedController

import scala.concurrent.Future

import reactivemongo.api.bson.BSONDocument

trait DataReadWithoutCriteriaController[Doc <: DocumentWithId, Model]
    extends InjectedController
    with DataReadController[Doc, Model]
    with WithFormBinding
    with WithExecutionContext {
  self: InjectedController =>
  protected def readService: DataReadSimpleService[Doc, Model]

  def getPaginatedData(
      queryOptions: QueryOptions
  )(implicit request: RequestType[_]): Future[Paginated[Model]] = {
    readService.find(BSONDocument(), queryOptions).map { dataWithTotalCount =>
      Paginated(
        data                  = dataWithTotalCount.data,
        totalCount            = dataWithTotalCount.totalCount,
        queryOptions          = queryOptions,
        rowToAttributesMapper = None
      )
    }
  }

}

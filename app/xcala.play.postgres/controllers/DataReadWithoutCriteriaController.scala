package xcala.play.postgres.controllers

import xcala.play.controllers.WithFormBinding
import xcala.play.models.Paginated
import xcala.play.models.QueryOptions
import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.services.DataReadWithoutCriteriaService
import xcala.play.utils.WithExecutionContext

import play.api.mvc.InjectedController

import scala.concurrent.Future

trait DataReadWithoutCriteriaController[Id, Entity <: EntityWithId[Id], Model]
    extends InjectedController
    with DataReadController[Id, Entity, Model]
    with WithFormBinding
    with WithExecutionContext {
  self: InjectedController =>
  protected def readService: DataReadWithoutCriteriaService[Id, Entity, Model]

  def getPaginatedData(
      queryOptions: QueryOptions
  )(implicit request: RequestType[_]): Future[Paginated[Model]] = {
    readService.find(queryOptions).map { dataWithTotalCount =>
      Paginated(
        data                  = dataWithTotalCount.data,
        totalCount            = dataWithTotalCount.totalCount,
        queryOptions          = queryOptions,
        rowToAttributesMapper = None
      )
    }
  }

}

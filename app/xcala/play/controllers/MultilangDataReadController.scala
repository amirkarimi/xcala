package xcala.play.controllers

import xcala.play.models._
import xcala.play.services.DataReadSimpleService
import xcala.play.utils.WithExecutionContext

import play.api.i18n.I18nSupport

import scala.concurrent.Future

import reactivemongo.api.bson._

trait MultilangDataReadController[Doc <: DocumentWithId with WithLang, Model]
    extends DataReadController[Doc, Model]
    with WithExecutionContext
    with I18nSupport {

  protected val readService: DataReadSimpleService[Doc, Model]

  override def getPaginatedData(
      queryOptions: QueryOptions
  )(implicit request: RequestType[_]): Future[Paginated[Model]] = {
    readService.find(BSONDocument("lang" -> request2Messages.lang.code), queryOptions).map {
      dataWithTotalCount =>
        Paginated(
          data                  = dataWithTotalCount.data,
          totalCount            = dataWithTotalCount.totalCount,
          queryOptions          = queryOptions,
          rowToAttributesMapper = None
        )
    }
  }

}

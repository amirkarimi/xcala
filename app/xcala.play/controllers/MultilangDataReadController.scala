package xcala.play.controllers

import xcala.play.models._
import xcala.play.services._
import xcala.play.utils.WithExecutionContext

import play.api.i18n.I18nSupport

import scala.concurrent.Future

import reactivemongo.api.bson._

trait MultilangDataReadController[A <: WithLang]
    extends DataReadController[A]
    with WithExecutionContext
    with I18nSupport {

  protected val readService: DataReadService[A]

  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    readService.find(BSONDocument("lang" -> request2Messages.lang.code), queryOptions).map { dataWithTotalCount =>
      Paginated(data = dataWithTotalCount.data, totalCount = dataWithTotalCount.totalCount, queryOptions = queryOptions)
    }
  }

}

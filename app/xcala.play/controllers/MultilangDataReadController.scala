package xcala.play.controllers

import play.api.i18n.{I18nSupport, Lang}
import reactivemongo.api.bson._
import xcala.play.models._
import xcala.play.services._

import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait MultilangDataReadController[A <: WithLang] extends DataReadController[A] with WithExecutionContext with I18nSupport {
  
  protected val readService: DataReadService[A]

  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    readService.find(BSONDocument("lang" -> request2Messages.lang.code), queryOptions).map { dataWithTotalCount =>
      Paginated(
        dataWithTotalCount.data,
        dataWithTotalCount.totalCount,
        queryOptions)
    }
  }
}
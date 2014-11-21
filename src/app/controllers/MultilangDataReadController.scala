package xcala.play.controllers

import play.api.i18n.Lang
import reactivemongo.bson._
import xcala.play.models._
import xcala.play.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait MultilangDataReadController[A <: WithLang] extends DataReadController[A] {
  
  protected val readService: DataReadService[A]

  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType, lang: Lang): Future[Paginated[A]] = {
    readService.find(BSONDocument("lang" -> lang.code), queryOptions).map { dataWithTotalCount =>
      Paginated(
        dataWithTotalCount.data,
        dataWithTotalCount.totalCount,
        queryOptions)
    }
  }
}
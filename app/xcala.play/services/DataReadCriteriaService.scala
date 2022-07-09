package xcala.play.services

import xcala.play.models._
import scala.concurrent.Future
import reactivemongo.api.bson.BSONDocument

trait DataReadCriteriaService[A, B] extends DataReadService[A] {
  def find(criteriaOpt: Option[B], queryOptions: QueryOptions): Future[DataWithTotalCount[A]]
}

trait DataReadCriteriaServiceImpl[A, B] extends DataReadCriteriaService[A, B] {
  def query(criteria: B): BSONDocument

  def find(criteriaOpt: Option[B], queryOptions: QueryOptions): Future[DataWithTotalCount[A]] = {
    val queryDocument = criteriaOpt.map(query).getOrElse(BSONDocument())
    find(queryDocument, queryOptions)
  }

}

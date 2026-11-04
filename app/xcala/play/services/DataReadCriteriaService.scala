package xcala.play.services

import xcala.play.models._
import xcala.play.models.DocumentWithId

import scala.concurrent.Future

import reactivemongo.api.bson.BSONDocument

trait DataReadWithCriteriaService[Doc <: DocumentWithId, Model, Criteria] extends DataReadService[Doc] {
  def find(criteriaOpt: Option[Criteria], queryOptions: QueryOptions): Future[DataWithTotalCount[Model]]
}

trait DataReadWithCriteriaServiceImpl[Doc <: DocumentWithId, Model, Criteria]
    extends DataReadWithCriteriaService[Doc, Model, Criteria]
    with DataReadSimpleService[Doc, Model] {
  def query(criteria: Criteria): BSONDocument

  def find(criteriaOpt: Option[Criteria], queryOptions: QueryOptions): Future[DataWithTotalCount[Model]] = {
    val queryDocument = criteriaOpt.map(query).getOrElse(BSONDocument())
    find(queryDocument, queryOptions)
  }

}

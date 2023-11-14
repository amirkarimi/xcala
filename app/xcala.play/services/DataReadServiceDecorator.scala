package xcala.play.services

import xcala.play.models.DataWithTotalCount
import xcala.play.models.DocumentWithId
import xcala.play.models.QueryOptions
import xcala.play.models.SortOptions
import xcala.play.services.DataReadSimpleService
import xcala.play.utils.WithExecutionContext

import scala.concurrent.Future

import reactivemongo.api.bson._

trait DataReadServiceDecorator[Doc <: DocumentWithId, Model]
    extends DataReadSimpleService[Doc, Model]
    with WithExecutionContext {
  protected val service: DataReadSimpleService[Doc, Doc] with DataSaveService[Doc, Doc] with DataRemoveService[Doc]

  def mapModel(source: Doc): Future[Model]

  def getIdFromModel(model: Model): Option[BSONObjectID]

  private def mapOptional(maybeModel: Option[Doc]): Future[Option[Model]] = maybeModel match {
    case None        => Future.successful(None)
    case Some(model) => mapModel(model).map(Some(_))
  }

  private def mapSeq(seq: Seq[Doc]): Future[List[Model]] = Future.sequence(seq.map(mapModel).toList)

  def findById(id: BSONObjectID): Future[Option[Model]] = service.findById(id).flatMap(mapOptional)

  def find(query: BSONDocument): Future[List[Model]] = service.find(query).flatMap(mapSeq)

  def find(query: BSONDocument, sort: BSONDocument): Future[List[Model]] = service.find(query, sort).flatMap(mapSeq)

  def findOne(query: BSONDocument): Future[Option[Model]] = service.findOne(query).flatMap(mapOptional)

  def count(query: BSONDocument): Future[Long] = service.count(query)

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[Model]] = {
    service.find(query, queryOptions).flatMap { dataWithTotalCount =>
      mapSeq(dataWithTotalCount.data).map { convertedData =>
        dataWithTotalCount.copy(data = convertedData)
      }
    }
  }

  def find(query: BSONDocument, sortOptions: SortOptions): Future[Seq[Model]] =
    service.find(query, sortOptions).flatMap(mapSeq)

}

package xcala.play.services

import scala.concurrent.Future
import reactivemongo.api.bson._
import xcala.play.models.{ QueryOptions, SortOptions, DataWithTotalCount }
import xcala.play.utils.WithExecutionContext

trait DataReadServiceDecorator[A, B] extends DataReadService[B] with WithExecutionContext {
  protected val service: DataDocumentHandler[A] 
    with DataReadService[A]
    with DataRemoveService 
    with DataSaveService[A]

  def mapModel(source: A): Future[B]
  
  def getIdFromModel(model: B): Option[BSONObjectID]
  
  private def mapOptional(maybeModel: Option[A]): Future[Option[B]] = maybeModel match {
    case None => Future.successful(None)
    case Some(model) => mapModel(model).map(Some(_))
  }
  
  private def mapSeq(seq: Seq[A]): Future[List[B]] = Future.sequence(seq.map(mapModel).toList)

  def findById(id: BSONObjectID): Future[Option[B]] = service.findById(id).flatMap(mapOptional)

  def find(query: BSONDocument): Future[List[B]] = service.find(query).flatMap(mapSeq)

  def find(query: BSONDocument, sort: BSONDocument): Future[List[B]] = service.find(query, sort).flatMap(mapSeq)

  def findOne(query: BSONDocument): Future[Option[B]] = service.findOne(query).flatMap(mapOptional)

  def count(query: BSONDocument): Future[Long] = service.count(query)

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[B]] = {
    service.find(query, queryOptions).flatMap { dataWithTotalCount =>
      mapSeq(dataWithTotalCount.data) map { convertedData => 
        dataWithTotalCount.copy(data = convertedData)
      }
    }
  }

  def find(query: BSONDocument, sortOptions: SortOptions): Future[Seq[B]] = service.find(query, sortOptions).flatMap(mapSeq)
}

package xcala.play.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.bson._
import reactivemongo.core.commands.LastError
import xcala.play.models.{ QueryOptions, DataWithTotalCount }
import reactivemongo.api.collections.GenericQueryBuilder

trait DataReadServiceDecorator[A, B] extends DataReadService[B] {
  val service: DataDocumentHandler[A] 
    with DataReadService[A] 
    with DataRemoveService 
    with DataSaveService[A]

  def mapModel(source: A): B
  
  def findQuery(query: BSONDocument): GenericQueryBuilder[BSONDocument, BSONDocumentReader, BSONDocumentWriter] = service.findQuery(query)

  def findById(id: BSONObjectID): Future[Option[B]] = service.findById(id).map(opt => opt.map(mapModel))
  
  def find(query: BSONDocument): Future[List[B]] = service.find(query).map(list => list.map(mapModel))
  
  def find(query: BSONDocument, sort: BSONDocument): Future[List[B]] = service.find(query, sort).map(list => list.map(mapModel))
  
  def findOne(query: BSONDocument): Future[Option[B]] = service.findOne(query).map(opt => opt.map(mapModel))
  
  def count(query: BSONDocument): Future[Int] = service.count(query)
  
  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[B]] = 
    service.find(query, queryOptions).map(dataWithTotalCount => dataWithTotalCount.copy(data = dataWithTotalCount.data.map(mapModel)))
  
  def getIdFromModel(model: B): Option[BSONObjectID]
}

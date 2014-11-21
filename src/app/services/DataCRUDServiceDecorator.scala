package xcala.play.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.bson._
import reactivemongo.core.commands.LastError
import xcala.play.models.{ QueryOptions, DataWithTotalCount }
import reactivemongo.api.collections.GenericQueryBuilder

trait DataCrudServiceDecorator[A, B] 
	extends DataReadServiceDecorator[A, B] 
  with DataRemoveService 
  with DataSaveService[B] {
  
  val service: DataDocumentHandler[A] 
		with DataReadService[A] 
		with DataRemoveService 
		with DataSaveService[A]

  def mapBackModel(source: B): A
  
  def copyBackModel(source: B, destination: A): A
  
	def remove(query: BSONDocument): Future[LastError] = service.remove(query)
  
  def insert(model: B) = service.insert(mapBackModel(model))
	
  def save(model: B) = {
    getIdFromModel(model) match {
      case Some(id) =>
        service.findById(id).flatMap {          
          case Some(originalModel) => service.save(copyBackModel(model, originalModel))
          case None => Future.failed(new Throwable("Save error: Model ID not found to be updated."))
        }
      case _ => 
        service.save(mapBackModel(model))
    }
  }
}

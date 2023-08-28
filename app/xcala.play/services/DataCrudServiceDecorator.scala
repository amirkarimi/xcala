package xcala.play.services

import xcala.play.utils.WithExecutionContext

import scala.concurrent.Future

import reactivemongo.api.bson._
import reactivemongo.api.commands.WriteResult

trait DataCrudServiceDecorator[A, B]
    extends DataReadServiceDecorator[A, B]
    with DataRemoveService
    with DataSaveService[B]
    with WithExecutionContext {

  val service: DataDocumentHandler[A] with DataReadService[A] with DataRemoveService with DataSaveService[A]

  def mapBackModel(source: B): Future[A]

  def copyBackModel(source: B, destination: A): Future[A]

  def remove(query: BSONDocument): Future[WriteResult] = service.remove(query)

  def insert(model: B): Future[BSONObjectID] = mapBackModel(model).flatMap(service.insert)

  def save(model: B): Future[BSONObjectID] = {
    getIdFromModel(model) match {
      case Some(id) =>
        service.findById(id).flatMap {
          case Some(originalModel) => copyBackModel(model, originalModel).flatMap(service.save)
          case None                => Future.failed(new Throwable("Save error: Model ID not found to be updated."))
        }
      case _        =>
        mapBackModel(model).flatMap(service.save)
    }
  }

}

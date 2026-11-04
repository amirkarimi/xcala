package xcala.play.services

import xcala.play.models.DocumentWithId
import xcala.play.utils.WithExecutionContext

import play.api.mvc.RequestHeader

import scala.concurrent.Future

import reactivemongo.api.bson._
import reactivemongo.api.commands.WriteResult

trait DataCrudServiceDecorator[Doc <: DocumentWithId, Model]
    extends DataReadServiceDecorator[Doc, Model]
    with DataSaveService[Doc, Model]
    with DataRemoveService[Doc]
    with WithExecutionContext {

  val service: DataReadSimpleService[Doc, Doc] with DataSaveService[Doc, Doc] with DataRemoveService[Doc]

  def mapBackModel(source: Model): Future[Doc]

  def copyBackModel(source: Model, destination: Doc): Future[Doc]

  def remove(query: BSONDocument): Future[WriteResult] = service.remove(query)

  def insert(model: Model): Future[BSONObjectID] = mapBackModel(model).flatMap(service.insert)

  def save(model: Model)(implicit requestHeader: RequestHeader): Future[BSONObjectID] = {
    getIdFromModel(model) match {
      case Some(id) =>
        service.findById(id).flatMap {
          case Some(originalModel) => copyBackModel(model, originalModel).flatMap(service.save)
          case None                => Future.failed(new Throwable("Save error: Model ID not found to be updated."))
        }
      case _ =>
        mapBackModel(model).flatMap(service.save)
    }
  }

}

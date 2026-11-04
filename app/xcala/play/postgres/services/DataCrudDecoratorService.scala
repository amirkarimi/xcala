package xcala.play.postgres.services

import xcala.play.postgres.models._
import xcala.play.utils.WithExecutionContext

import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.language.reflectiveCalls

/** Decorator service.
  */
trait DataCrudDecoratorService[Id, Entity <: EntityWithId[Id], Model <: { val id: Option[Id] }]
    extends DataReadSimpleService[Id, Entity, Model]
    with DataSaveService[Id, Entity, Model]
    with DataRemoveService[Id, Entity]
    with WithExecutionContext {

  val service: DataReadSimpleService[Id, Entity, Entity]
    with DataSaveService[Id, Entity, Entity]
    with DataRemoveService[Id, Entity]

  def mapModel(source     : Entity)                    : Future[Model]
  def mapBackModel(source : Model)                     : Future[Entity]
  def copyBackModel(source: Model, destination: Entity): Future[Entity]

  def findById(id: Id): Future[Option[Model]] = {
    service.findById(id).flatMap {
      case None         => Future.successful(None)
      case Some(entity) => mapModel(entity).map(Some(_))
    }
  }

  def findAll: Future[Seq[Model]] = {
    service.findAll.flatMap { items =>
      Future.sequence(
        items.map(mapModel)
      )
    }
  }

  def insert(model: Model): Future[Id] = {
    mapBackModel(model).flatMap(service.insert)
  }

  def insertMany(models: Seq[Model]): Future[Int] =
    Future
      .sequence {
        models.map(mapBackModel)
      }
      .flatMap { entities =>
        service.insertMany(entities)
      }

  def updateOrInsert(model: Model): Future[Option[Id]] = {
    mapBackModel(model).flatMap(service.updateOrInsert)
  }

  def update(model: Model): Future[Int] = {
    model.id
      .map { id =>
        service.findById(id).flatMap {
          case None           => Future.successful(0)
          case Some(dbEntity) => copyBackModel(model, dbEntity).flatMap(service.update)
        }
      }
      .getOrElse(Future.successful(0))
  }

  def delete(id: Id)(implicit
      requestHeader: RequestHeader
  ): Future[Int] = service.delete(id)

}

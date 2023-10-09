package xcala.play.postgres.services

import xcala.play.postgres.models._
import xcala.play.postgres.utils.WithExecutionContext

import scala.concurrent.Future

/** Decorator service.
  */
trait DataCrudServiceDecorator[A <: EntityWithId, B <: EntityWithId]
    extends DataCrudService[B]
    with WithExecutionContext {

  val service: DataCrudService[A]

  def mapModel(source: A): Future[B]
  def mapBackModel(source: B): Future[A]
  def copyBackModel(source: B, destination: A): Future[A]

  def findById(id: Long): Future[Option[B]] = {
    service.findById(id).flatMap {
      case None         => Future.successful(None)
      case Some(entity) => mapModel(entity).map(Some(_))
    }
  }

  def findAll: Future[Seq[B]] = {
    service.findAll.flatMap { items =>
      Future.sequence(
        items.map(mapModel)
      )
    }
  }

  def insert(model: B): Future[Long] = {
    mapBackModel(model).flatMap(service.insert)
  }

  def insertMany(models: Seq[B]): Future[Int] =
    Future
      .sequence {
        models.map(mapBackModel)
      }
      .flatMap { entities =>
        service.insertMany(entities)
      }

  def updateOrInsert(model: B): Future[Option[Long]] = {
    mapBackModel(model).flatMap(service.updateOrInsert)
  }

  def update(model: B): Future[Int] = {
    model.id
      .map { id =>
        service.findById(id).flatMap {
          case None           => Future.successful(0)
          case Some(dbEntity) => copyBackModel(model, dbEntity).flatMap(service.update)
        }
      }
      .getOrElse(Future.successful(0))
  }

  def delete(id: Long): Future[Int] = service.delete(id)
}

package xcala.play.postgres.services

import xcala.play.postgres.entities.TableDefinitionWithUUID
import xcala.play.postgres.models._
import xcala.play.postgres.utils.WithExecutionContext

import java.util.UUID
import scala.concurrent.Future

/** Represents the read functionality of the service.
  */
trait DataReadServiceWithUUID[A <: EntityWithUUId] {
  def findById(id: UUID): Future[Option[A]]
  def findAll: Future[Seq[A]]
}

/** Represents the read functionality of the service with criteria support.
  */
trait DataReadCriteriaServiceWithUUID[A, B] {
  def find(criteria: B, queryOptions: QueryOptions): Future[DataWithTotalCount[A]]
}

/** Represents the CRUD functionality of the service.
  */
trait DataCrudServiceWithUUID[A <: EntityWithUUId] extends DataReadServiceWithUUID[A] with WithExecutionContext {
  def insert(entity: A): Future[UUID]
  def updateOrInsert(entity: A): Future[Option[UUID]]
  def update(entity: A): Future[Int]
  def delete(id: UUID): Future[Int]
}

/** Service query functionality.
  */
trait DataQueryServiceWithUUID[A <: EntityWithUUId] extends DataService {
  val tableDefinition: TableDefinitionWithUUID[A]

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  val tableQuery: TableQuery[TableDef] = tableDefinition.tableQuery
}

/** Implementation of the read service.
  */
trait DataReadServiceImplWithUUID[A <: EntityWithUUId]
    extends DataReadServiceWithUUID[A]
    with DataQueryServiceWithUUID[A] {

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  protected def filterQueryById(id: UUID): Query[TableDef, A, Seq] = {
    tableQuery.filter(_.id === id)
  }

  def findById(id: UUID): Future[Option[A]] = {
    val action = filterQueryById(id).result.headOption
    db.run(action)
  }

  def findAll: Future[Seq[A]] = {
    db.run(tableQuery.result)
  }

}

/** Implementation of the CRUD service.
  */
trait DataCrudServiceImplWithUUID[A <: EntityWithUUId]
    extends DataCrudServiceWithUUID[A]
    with DataReadServiceImplWithUUID[A]
    with WithExecutionContext {
  import tableDefinition.profile.api._

  def insert(entity: A): Future[UUID] = {
    val action = tableQuery.returning(tableQuery.map(_.id)) += entity
    db.run(action)
  }

  def updateOrInsert(entity: A): Future[Option[UUID]] = {
    val action = tableQuery.returning(tableQuery.map(_.id)).insertOrUpdate(entity)
    db.run(action).map(_.orElse(entity.id))
  }

  def update(entity: A): Future[Int] = {
    entity.id match {
      case None     => Future.successful(0)
      case Some(id) =>
        val action = filterQueryById(id).update(entity)
        db.run(action)
    }
  }

  def delete(id: UUID): Future[Int] = {
    val action = filterQueryById(id).delete
    db.run(action)
  }

}

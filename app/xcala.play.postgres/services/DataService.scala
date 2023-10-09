package xcala.play.postgres.services

import xcala.play.postgres.entities.TableDefinition
import xcala.play.postgres.models.{DataWithTotalCount, EntityWithId, QueryOptions}
import xcala.play.postgres.utils.WithExecutionContext

import play.api.mvc.Request

import scala.concurrent.Future

import slick.jdbc.JdbcBackend

/** Represents the read functionality of the service.
  */
trait DataReadService[A <: EntityWithId] {
  def findById(id: Long): Future[Option[A]]
  def findAll: Future[Seq[A]]
}

/** Represents the read functionality of the service with criteria support.
  */
trait DataReadCriteriaService[A, B] {
  def find(criteria: B, queryOptions: QueryOptions)(implicit request: Request[_]): Future[DataWithTotalCount[A]]
}

/** Represents the CRUD functionality of the service.
  */
trait DataCrudService[A <: EntityWithId] extends DataReadService[A] with WithExecutionContext {
  def insert(entity: A): Future[Long]
  def insertMany(entities: Seq[A]): Future[Int]
  def updateOrInsert(entity: A): Future[Option[Long]]
  def update(entity: A): Future[Int]
  def delete(id: Long): Future[Int]
}

/** Data driven service.
  */
trait DataService {
  protected val dbConfig: DbConfig
  protected val db: JdbcBackend#DatabaseDef = dbConfig.db
}

/** Service query functionality.
  */
trait DataQueryService[A <: EntityWithId] extends DataService {
  val tableDefinition: TableDefinition[A]

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  val tableQuery: TableQuery[TableDef] = tableDefinition.tableQuery
}

/** Implementation of the read service.
  */
trait DataReadServiceImpl[A <: EntityWithId] extends DataReadService[A] with DataQueryService[A] {

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  protected def filterQueryById(id: Long): Query[TableDef, A, Seq] = {
    tableQuery.filter(_.id === id)
  }

  protected def filterQueryByRegex(
      field: tableDefinition.profile.api.Table[A] => Rep[String],
      pattern: String
  ): Query[TableDef, A, Seq] = {
    tableQuery.filter { x =>
      field(x) ~ pattern
    }
  }

  def findWithRegex(
      field: tableDefinition.profile.api.Table[A] => Rep[String],
      pattern: String
  ): Future[Seq[A]] = {
    val action = filterQueryByRegex(field, pattern).result
    db.run(action)
  }

  def findById(id: Long): Future[Option[A]] = {
    val action = filterQueryById(id).result.headOption
    db.run(action)
  }

  def findAll: Future[Seq[A]] = {
    db.run(tableQuery.result)
  }

}

/** Implementation of the CRUD service.
  */
trait DataCrudServiceImpl[A <: EntityWithId]
    extends DataCrudService[A]
    with DataReadServiceImpl[A]
    with WithExecutionContext {
  import tableDefinition.profile.api._

  def insert(entity: A): Future[Long] = {
    val action = tableQuery.returning(tableQuery.map(_.id)) += entity
    db.run(action)
  }

  def insertMany(entities: Seq[A]): Future[Int] = {
    val action = tableQuery ++= entities
    db.run(action).map(_.getOrElse(0))
  }

  def updateOrInsert(entity: A): Future[Option[Long]] = {
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

  def delete(id: Long): Future[Int] = {
    val action = filterQueryById(id).delete
    db.run(action)
  }

}

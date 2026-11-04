package xcala.play.postgres.services

import xcala.play.models.{DataWithTotalCount, QueryOptions}
import xcala.play.postgres.entities.TableDefinition
import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.utils.QueryHelpers._
import xcala.play.utils.WithExecutionContext

import play.api.mvc.Request
import play.api.mvc.RequestHeader

import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import slick.jdbc.JdbcBackend

/** Data driven service.
  */
trait DataService {
  protected val dbConfig: DbConfig
  protected val db: JdbcBackend#Database = dbConfig.db
}

/** Service query functionality.
  */
trait DataQueryService[Id, Entity <: EntityWithId[Id]] extends DataService {
  val tableDefinition: TableDefinition[Id, Entity]

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  val tableQuery: TableQuery[TableDef] = tableDefinition.tableQuery

  protected def filterQueryById(id: Id): Query[TableDef, Entity, Seq]
  protected def mappedToIdColumnQuery  : Query[Rep[Id], Id, Seq]

  def distinct[F, G, T](
      f             : tableDefinition.TableDef => Rep[Option[F]],
      maybeCondition: Option[tableDefinition.TableDef => Rep[Boolean]] = None
  )(
      implicit
      shape: Shape[_ <: FlatShapeLevel, Rep[Option[F]], Option[T], G],
      ec   : ExecutionContext
  ): Future[Set[T]] =
    db.run(
      tableQuery
        .optionalQuery { query =>
          maybeCondition.map { condition =>
            query.filter(condition)
          }
        }
        .map(f)
        .distinct.result
    ).map(_.flatten.toSet)

}

trait DataQueryWithLongIdService[Entity <: EntityWithId[Long]] extends DataQueryService[Long, Entity] {

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  protected def filterQueryById(id: Long): Query[TableDef, Entity, Seq] =
    tableQuery.filter(_.id === id)

  protected def mappedToIdColumnQuery: Query[Rep[Long], Long, Seq] =
    tableQuery.map(_.id)

}

trait DataQueryWithUUIdService[Entity <: EntityWithId[UUID]] extends DataQueryService[UUID, Entity] {

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  protected def filterQueryById(id: UUID): Query[TableDef, Entity, Seq] =
    tableQuery.filter(_.id === id)

  protected def mappedToIdColumnQuery: Query[Rep[UUID], UUID, Seq] =
    tableQuery.map(_.id)

}

sealed trait DataReadService[Id, Entity <: EntityWithId[Id]] extends DataService

/** Represents the read functionality of the service.
  */
trait DataReadSimpleService[Id, Entity <: EntityWithId[Id], Model] extends DataReadService[Id, Entity] {
  def findById(id: Id): Future[Option[Model]]
  def findAll         : Future[Seq[Model]]
}

/** Implementation of the read service.
  */
trait DataReadSimpleServiceImpl[Id, Entity <: EntityWithId[Id]]
    extends DataQueryService[Id, Entity]
    with DataReadSimpleService[Id, Entity, Entity]
    with WithExecutionContext {

  import tableDefinition.TableDef
  import tableDefinition.profile.api._

  protected def filterQueryByRegex(
      field  : tableDefinition.profile.api.Table[Entity] => Rep[String],
      pattern: String
  ): Query[TableDef, Entity, Seq] = {
    tableQuery.filter { x =>
      field(x) ~ pattern
    }
  }

  def findWithRegex(
      field  : tableDefinition.profile.api.Table[Entity] => Rep[String],
      pattern: String
  ): Future[Seq[Entity]] = {
    val action = filterQueryByRegex(field, pattern).result
    db.run(action)
  }

  def findById(id: Id): Future[Option[Entity]] = {
    val action = filterQueryById(id).result.headOption
    db.run(action)
  }

  def findAll: Future[Seq[Entity]] = {
    db.run(tableQuery.result)
  }

}

/** Represents the read functionality of the service with criteria support.
  */
trait DataReadWithCriteriaService[Id, Entity <: EntityWithId[Id], Model, Criteria]
    extends DataReadService[Id, Entity] {

  def find(criteria: Criteria, queryOptions: QueryOptions)(implicit
      request: Request[_]
  ): Future[DataWithTotalCount[Model]]

}

trait DataReadWithoutCriteriaService[Id, Entity <: EntityWithId[Id], Model]
    extends DataReadService[Id, Entity] {

  def find(queryOptions: QueryOptions)(implicit
      request: Request[_]
  ): Future[DataWithTotalCount[Model]]

}

trait DataReadWithoutCriteriaServiceImpl[Id, Entity <: EntityWithId[Id]]
    extends DataQueryService[Id, Entity]
    with DataReadWithoutCriteriaService[Id, Entity, Entity]
    with WithExecutionContext {

  import tableDefinition.profile.api._

  def find(queryOptions: QueryOptions)(implicit
      request: Request[_]
  ): Future[DataWithTotalCount[Entity]] = {
    import xcala.play.postgres.utils.QueryHelpers._
    val query          = tableDefinition.tableQuery
    val queryPaginated = query.paginated(queryOptions)
    for {
      items <- db.run(queryPaginated.result)
      count <- db.run(query.length.result)
    } yield {
      DataWithTotalCount(items, count)
    }
  }

}

trait DataSaveService[Id, Entity <: EntityWithId[Id], Model] extends DataService {
  def updateOrInsert(model: Model)     : Future[Option[Id]]
  def update(model        : Model)     : Future[Int]
  def insert(model        : Model)     : Future[Id]
  def insertMany(model    : Seq[Model]): Future[Int]
}

trait DataSaveServiceImpl[Id, Entity <: EntityWithId[Id]]
    extends DataQueryService[Id, Entity]
    with DataSaveService[Id, Entity, Entity]
    with WithExecutionContext {

  import tableDefinition.profile.api._

  def insert(entity: Entity): Future[Id] = {
    val action = tableQuery.returning(mappedToIdColumnQuery) += entity
    db.run(action)
  }

  def insertMany(entities: Seq[Entity]): Future[Int] = {
    val action = tableQuery ++= entities
    db.run(action).map(_.getOrElse(0))
  }

  def updateOrInsert(entity: Entity): Future[Option[Id]] = {
    val action = tableQuery.returning(mappedToIdColumnQuery).insertOrUpdate(entity)
    db.run(action).map(_.orElse(entity.id))
  }

  def update(entity: Entity): Future[Int] = {
    entity.id match {
      case None => Future.successful(0)
      case Some(id) =>
        val action = filterQueryById(id).update(entity)
        db.run(action)
    }
  }

}

trait DataRemoveService[Id, Entity <: EntityWithId[Id]] extends DataService {

  def delete(id: Id)(implicit
      requestHeader: RequestHeader
  ): Future[Int]

}

trait DataRemoveServiceImpl[Id, Entity <: EntityWithId[Id]]
    extends DataQueryService[Id, Entity]
    with DataRemoveService[Id, Entity] {
  import tableDefinition.profile.api._

  def delete(id: Id)(
      implicit requestHeader: RequestHeader
  ): Future[Int] = {
    val action = filterQueryById(id).delete
    db.run(action)
  }

}

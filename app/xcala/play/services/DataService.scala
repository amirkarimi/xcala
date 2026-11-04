package xcala.play.services

import xcala.play.extensions.BSONHandlers._
import xcala.play.models._
import xcala.play.models.DocumentWithId
import xcala.play.utils.WithExecutionContext

import play.api.mvc.RequestHeader

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.joda.time.DateTime
import reactivemongo.api._
import reactivemongo.api.FailoverStrategy
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection._
import reactivemongo.api.commands._

/** Represents the data service foundation.
  */
trait DataService extends WithExecutionContext {}

trait DatabaseAccess extends DataService {
  protected def databaseConfig: DatabaseConfig
  protected lazy val dbFuture : Future[DB] = databaseConfig.databaseFuture
}

/** Represents the service which works with a collection
  */
trait DataCollectionService extends DatabaseAccess {

  protected val collectionName: String

  protected lazy val collectionFuture: Future[BSONCollection] = getCollection

  private[services] def getCollection: Future[BSONCollection] = {
    dbFuture.map(_.collection(collectionName)).map { coll =>
      onCollectionInitialized(coll)
      coll
    }
  }

  protected def onCollectionInitialized(collection: BSONCollection): Unit = {}

  def distinct(fieldName: String, query: Option[BSONDocument] = None): Future[Seq[BSONValue]] = {
    val command = BSONDocument("distinct" -> collectionName, "key" -> fieldName, "query" -> query)
    dbFuture
      .flatMap(
        _.runCommand(command, FailoverStrategy.default).cursor[BSONDocument](
          ReadPreference.primaryPreferred
        ).head
      )
      .map { doc =>
        doc.getAsOpt[BSONArray]("values").toSeq.flatMap(_.values)
      }
  }

  def findCommand(query: BSONDocument): Future[BSONDocument] = {
    val command =
      BSONDocument("find" -> collectionName, "filter" -> query)
    dbFuture
      .flatMap(
        _.runCommand(command, FailoverStrategy.default).cursor[BSONDocument](
          ReadPreference.primaryPreferred
        ).head
      )
  }

}

trait WithDbCommand extends DatabaseAccess {

  def dbCommand(commandDoc: BSONDocument)(implicit ec: ExecutionContext): Future[BSONDocument] =
    dbFuture.flatMap(
      _.runCommand(commandDoc, FailoverStrategy.default).cursor[BSONDocument](
        ReadPreference.primaryPreferred
      ).head
    )

}

trait WithExternalCollectionAccess extends DatabaseAccess {

  protected def collection(collectionName: String): Future[BSONCollection] =
    dbFuture.map(_.collection(collectionName))

}

/** Represents the document handler.
  */
trait DataDocumentHandler[Doc] {
  implicit val documentHandler: BSONDocumentReader[Doc] with BSONDocumentWriter[Doc] with BSONHandler[Doc]
}

trait DataReadService[Doc <: DocumentWithId] extends DataService

/** Represents the read functionality of Crud service.
  */
trait DataReadSimpleService[Doc <: DocumentWithId, Model] extends DataReadService[Doc] {
  def findAll: Future[List[Model]] = find(BSONDocument())

  def findOne(query: BSONDocument): Future[Option[Model]]

  def findById(id: BSONObjectID): Future[Option[Model]]

  def find(query: BSONDocument): Future[List[Model]]

  def find(query: BSONDocument, sort: BSONDocument): Future[List[Model]]

  def count(query: BSONDocument): Future[Long]

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[Model]]

  def find(query: BSONDocument, sortOptions: SortOptions): Future[Seq[Model]]

  def findInIds(ids: Set[BSONObjectID]): Future[List[Model]] =
    find(
      BSONDocument(
        "_id" -> BSONDocument("$in" -> ids)
      )
    )

}

/** Represents the Read service implementation
  */
trait DataReadSimpleServiceImpl[Doc <: DocumentWithId]
    extends DataCollectionService
    with DataDocumentHandler[Doc]
    with DataReadSimpleService[Doc, Doc] {

  def findQuery(query: BSONDocument): Future[BSONCollection#QueryBuilder] =
    collectionFuture.map(_.find(query))

  def findById(id: BSONObjectID): Future[Option[Doc]] =
    collectionFuture.flatMap(_.find(BSONDocument("_id" -> id)).cursor[Doc]().headOption)

  def find(query: BSONDocument): Future[List[Doc]] =
    collectionFuture.flatMap(_.find(query).cursor[Doc]().collect[List]())

  def find(query: BSONDocument, sort: BSONDocument): Future[List[Doc]] =
    collectionFuture.flatMap(_.find(query).sort(sort).cursor[Doc]().collect[List]())

  def findOne(query: BSONDocument): Future[Option[Doc]] = collectionFuture.flatMap(_.find(query).one[Doc])

  def count(query: BSONDocument): Future[Long] = collectionFuture.flatMap(_.count(selector = Some(query)))

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[Doc]] = {
    val sortDocs = applyDefaultSort(queryOptions.sortInfos).map { sortInfo =>
      sortInfo.field -> BSONInteger(sortInfo.direction)
    }

    val queryBuilderFuture = collectionFuture.map(
      _.find(query)
        .skip(queryOptions.startRowIndex)
        .batchSize(queryOptions.pageSize)
        .sort(BSONDocument(sortDocs))
    )

    for {
      queryBuilder <- queryBuilderFuture
      data         <- queryBuilder.cursor[Doc]().collect[List](queryOptions.pageSize)
      totalCount   <- count(query)
    } yield DataWithTotalCount(data, totalCount)
  }

  def find(query: BSONDocument, sortOptions: SortOptions): Future[List[Doc]] = {
    val sortDocs = applyDefaultSort(sortOptions.sortInfos).map { sortInfo =>
      sortInfo.field -> BSONInteger(sortInfo.direction)
    }

    collectionFuture.flatMap(
      _.find(query)
        .sort(BSONDocument(sortDocs))
        .cursor[Doc]()
        .collect[List]()
    )
  }

  def findInIds(ids: Seq[BSONObjectID]): Future[List[Doc]] =
    find(BSONDocument("_id" -> BSONDocument("$in" -> ids)))

  protected def applyDefaultSort(sortInfos: Seq[SortInfo]): Seq[SortInfo] =
    sortInfos match {
      case Nil => defaultSort
      case _   => sortInfos
    }

  protected def defaultSort: Seq[SortInfo] = Seq(SortInfo("_id"))

}

/** Represents the create or update functionality of the Crud service.
  */
trait DataSaveService[Doc <: DocumentWithId, Model] {
  def insert(model: Model)                                       : Future[BSONObjectID]
  def save(model  : Model)(implicit requestHeader: RequestHeader): Future[BSONObjectID]
}

trait DataSaveServiceImpl[Doc <: DocumentWithId]
    extends DataSaveService[Doc, Doc]
    with DataDocumentHandler[Doc]
    with DataCollectionService {

  private def getDocWithId(model: Doc): (BSONDocument, BSONObjectID) = {
    val fieldName = "_id"
    val doc       = documentHandler.writeOpt(model).get
    val objectId  = doc.getAsOpt[BSONObjectID](fieldName).getOrElse(BSONObjectID.generate())
    val newDoc = BSONDocument(
      doc.elements.filter(_.name != fieldName).map(e => (e.name, e.value)) :+ (fieldName -> objectId)
    )
    (newDoc, objectId)
  }

  private def setUpdateTime(doc: BSONDocument): BSONDocument = {
    val updateTime = DateTime.now
    BSONDocument(
      doc.elements
        .filter(_.name != DataCrudService.UpdateTimeField)
        .map(e => (e.name, e.value)) :+
        (DataCrudService.UpdateTimeField -> BSONDateTime(updateTime.getMillis))
    )
  }

  private def setCreateAndUpdateTime(doc: BSONDocument): BSONDocument = {
    // Set create time if it wasn't available
    val createTime = doc.getAsOpt[DateTime](DataCrudService.CreateTimeField).getOrElse(DateTime.now)
    val docWithCreateTime = BSONDocument(
      doc.elements.filter(_.name != DataCrudService.CreateTimeField).map(e => (e.name, e.value)) :+
        (DataCrudService.CreateTimeField -> BSONDateTime(createTime.getMillis))
    )

    // Always set update time
    setUpdateTime(docWithCreateTime)
  }

  def insert(model: Doc): Future[BSONObjectID] = {
    val (newDoc, objectId) = getDocWithId(model)

    collectionFuture.flatMap(_.insert.one(setCreateAndUpdateTime(newDoc)).map(_ => objectId))
  }

  def bulkInsert(models: Seq[Doc]): Future[(BSONCollection#MultiBulkWriteResult, Seq[BSONObjectID])] = {

    val (newDocs, newIds) = models.map(getDocWithId).unzip

    collectionFuture.flatMap(_.insert.many(newDocs.map(setCreateAndUpdateTime))).map(_ -> newIds)

  }

  def save(model: Doc)(implicit requestHeader: RequestHeader): Future[BSONObjectID] = {
    val (newDoc, objectId) = getDocWithId(model)
    val updateDoc          = setCreateAndUpdateTime(newDoc)

    collectionFuture.flatMap(_.update.one(BSONDocument("_id" -> objectId), updateDoc, upsert = true)).map(_ =>
      objectId
    )
  }

  def update(
      selector     : BSONDocument,
      update       : BSONDocument,
      upsert       : Boolean = false,
      multi        : Boolean = false,
      setUpdateTime: Boolean = true
  ): Future[WriteResult] = {
    val finalUpdateDoc = setUpdateTime match {
      case false => update
      case true =>
        val updateTime = DateTime.now
        update ++
          BSONDocument(
            "$set" -> BSONDocument(DataCrudService.UpdateTimeField -> BSONDateTime(updateTime.getMillis))
          )
    }

    collectionFuture.flatMap(_.update.one(selector, finalUpdateDoc, upsert = upsert, multi = multi))
  }

}

/** Represents the remove functionality of the Crud service.
  */
trait DataRemoveService[Doc <: DocumentWithId] {
  def remove(id   : BSONObjectID): Future[WriteResult] = remove(BSONDocument("_id" -> id))
  def remove(query: BSONDocument): Future[WriteResult]
}

trait DataRemoveServiceImpl[Doc <: DocumentWithId] extends DataCollectionService with DataRemoveService[Doc] {
  def remove(query: BSONDocument): Future[WriteResult] = collectionFuture.flatMap(_.delete.one(query))
}

object DataCrudService {
  val UpdateTimeField: String = "updateTime"
  val CreateTimeField: String = "createTime"
}

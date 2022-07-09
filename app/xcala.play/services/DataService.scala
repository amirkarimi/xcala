package xcala.play.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import reactivemongo.api.bson._
import reactivemongo.api.FailoverStrategy
import reactivemongo.api._
import reactivemongo.api.bson.collection._
import xcala.play.models._
import xcala.play.extensions.BSONHandlers._
import org.joda.time.DateTime
import reactivemongo.api.commands._
import xcala.play.utils.WithExecutionContext

/** Represents the data service foundation.
  */
trait DataService extends WithExecutionContext {
  private[services] def databaseConfig: DatabaseConfig
  private[services] lazy val dbFuture: Future[DB] = databaseConfig.databaseFuture
}

/** Represents the service which works with a collection
  */
trait DataCollectionService extends DataService {
  private[services] val collectionName: String

  private[services] lazy val collectionFuture: Future[BSONCollection] = getCollection

  private[services] def getCollection = {
    dbFuture.map(_.collection(collectionName)).map { coll =>
      onCollectionInitialized(coll)
      coll
    }
  }

  protected def onCollectionInitialized(collection: BSONCollection) = {}
}

trait WithDbCommand extends DataService {

  def dbCommand(commandDoc: BSONDocument)(implicit ec: ExecutionContext) =
    dbFuture.flatMap(
      _.runCommand(commandDoc, FailoverStrategy.default).cursor[BSONDocument](ReadPreference.primaryPreferred).head
    )

}

trait WithExternalCollectionAccess extends DataService {
  protected def collection(collectionName: String) = dbFuture.map(_.collection(collectionName))
}

/** Represents the document handler.
  */
trait DataDocumentHandler[A] {
  implicit val documentHandler: BSONDocumentReader[A] with BSONDocumentWriter[A] with BSONHandler[A]
}

/** Represents the read functionality of Crud service.
  */
trait DataReadService[A] {
  def findAll: Future[List[A]] = find(BSONDocument())

  def findOne(query: BSONDocument): Future[Option[A]]

  def findById(id: BSONObjectID): Future[Option[A]]

  def find(query: BSONDocument): Future[List[A]]

  def find(query: BSONDocument, sort: BSONDocument): Future[List[A]]

  def count(query: BSONDocument): Future[Long]

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[A]]

  def find(query: BSONDocument, sortOptions: SortOptions): Future[Seq[A]]
}

/** Represents the remove functionality of the Crud service.
  */
trait DataRemoveService {
  def remove(id: BSONObjectID): Future[WriteResult] = remove(BSONDocument("_id" -> id))
  def remove(query: BSONDocument): Future[WriteResult]
}

/** Represents the create or update functionality of the Crud service.
  */
trait DataSaveService[A] {
  def insert(model: A): Future[BSONObjectID]
  def save(model: A): Future[BSONObjectID]
}

/** Represents the Read service implementation
  */
trait DataReadServiceImpl[A] extends DataCollectionService with DataDocumentHandler[A] with DataReadService[A] {

  def findQuery(query: BSONDocument) = collectionFuture.map(_.find(query))

  def findById(id: BSONObjectID): Future[Option[A]] =
    collectionFuture.flatMap(_.find(BSONDocument("_id" -> id)).cursor[A]().headOption)

  def find(query: BSONDocument): Future[List[A]] = collectionFuture.flatMap(_.find(query).cursor[A]().collect[List]())

  def find(query: BSONDocument, sort: BSONDocument): Future[List[A]] =
    collectionFuture.flatMap(_.find(query).sort(sort).cursor[A]().collect[List]())

  def findOne(query: BSONDocument): Future[Option[A]] = collectionFuture.flatMap(_.find(query).one[A])

  def count(query: BSONDocument): Future[Long] = collectionFuture.flatMap(_.count(Some(query)))

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[A]] = {
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
      data         <- queryBuilder.cursor[A]().collect[List](queryOptions.pageSize)
      totalCount   <- count(query)
    } yield DataWithTotalCount(data, totalCount)
  }

  def find(query: BSONDocument, sortOptions: SortOptions): Future[List[A]] = {
    val sortDocs = applyDefaultSort(sortOptions.sortInfos).map { sortInfo =>
      sortInfo.field -> BSONInteger(sortInfo.direction)
    }

    collectionFuture.flatMap(
      _.find(query)
        .sort(BSONDocument(sortDocs))
        .cursor[A]()
        .collect[List]()
    )
  }

  def distinct(fieldName: String, query: Option[BSONDocument] = None): Future[Seq[BSONValue]] = {
    val command = BSONDocument("distinct" -> collectionName, "key" -> fieldName, "query" -> query)
    dbFuture
      .flatMap(
        _.runCommand(command, FailoverStrategy.default).cursor[BSONDocument](ReadPreference.primaryPreferred).head
      )
      .map { doc =>
        doc.getAsOpt[BSONArray]("values").toSeq.flatMap(_.values)
      }
  }

  protected def applyDefaultSort(sortInfos: List[SortInfo]): List[SortInfo] = sortInfos match {
    case Nil => defaultSort
    case _   => sortInfos
  }

  protected def defaultSort: List[SortInfo] = Nil
}

/** Represents the CRUD service.
  */
trait DataCrudService[A]
    extends DataCollectionService
    with DataDocumentHandler[A]
    with DataReadServiceImpl[A]
    with DataRemoveService
    with DataSaveService[A] {

  def remove(query: BSONDocument): Future[WriteResult] = collectionFuture.flatMap(_.delete.one(query))

  def insert(model: A): Future[BSONObjectID] = {
    val (newDoc, objectId) = getDocWithId(model)

    collectionFuture.flatMap(_.insert.one(setCreateAndUpdateTime(newDoc)).map(_ => objectId))
  }

  def save(model: A): Future[BSONObjectID] = {
    val (newDoc, objectId) = getDocWithId(model)
    val updateDoc          = setCreateAndUpdateTime(newDoc)

    collectionFuture.flatMap(_.update.one(BSONDocument("_id" -> objectId), updateDoc, upsert = true)).map(_ => objectId)
  }

  def update(
      selector: BSONDocument,
      update: BSONDocument,
      upsert: Boolean = false,
      multi: Boolean = false,
      setUpdateTime: Boolean = true
  ): Future[WriteResult] = {
    val finalUpdateDoc = setUpdateTime match {
      case false => update
      case true =>
        val updateTime = DateTime.now
        update ++ BSONDocument(
          "$set" -> BSONDocument(DataCrudService.UpdateTimeField -> BSONDateTime(updateTime.getMillis))
        )
    }

    collectionFuture.flatMap(_.update.one(selector, finalUpdateDoc, upsert = upsert, multi = multi))
  }

  private def getDocWithId(model: A) = {
    val fieldName = "_id"
    val doc       = documentHandler.writeOpt(model).get
    val objectId  = doc.getAsOpt[BSONObjectID](fieldName).getOrElse(BSONObjectID.generate)
    val newDoc = BSONDocument(
      doc.elements.filter(_.name != fieldName).map(e => (e.name, e.value)) :+ (fieldName -> objectId)
    )
    (newDoc, objectId)
  }

  private def setCreateAndUpdateTime(doc: BSONDocument) = {
    // Set create time if it wasn't available
    val createTime = doc.getAsOpt[DateTime](DataCrudService.CreateTimeField).getOrElse(DateTime.now)
    val docWithCreateTime = BSONDocument(
      doc.elements.filter(_.name != DataCrudService.CreateTimeField).map(e => (e.name, e.value)) :+
        (DataCrudService.CreateTimeField -> BSONDateTime(createTime.getMillis))
    )

    // Always set update time
    setUpdateTime(docWithCreateTime)
  }

  private def setUpdateTime(doc: BSONDocument) = {
    val updateTime = DateTime.now
    BSONDocument(
      doc.elements
        .filter(_.name != DataCrudService.UpdateTimeField)
        .map(e => (e.name, e.value)) :+ (DataCrudService.UpdateTimeField -> BSONDateTime(updateTime.getMillis))
    )
  }

}

object DataCrudService {
  val UpdateTimeField = "updateTime"
  val CreateTimeField = "createTime"
}

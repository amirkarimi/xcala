package xcala.play.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import reactivemongo.bson._
import reactivemongo.api._
import reactivemongo.core.commands._
import reactivemongo.api.collections.default._
import reactivemongo.api.collections._
import play.api.Play
import play.api.libs.iteratee.Enumerator
import xcala.play.models._
import xcala.play.extensions.BSONHandlers._
import reactivemongo.api.gridfs.GridFS
import org.joda.time.DateTime
import xcala.play.utils.WithExecutionContext

/**
 * Represents the data service foundation.
 */
trait DataService extends WithExecutionContext {
  protected def databaseConfig: DatabaseConfig = DefaultDatabaseConfig
  
  private[services] lazy val db: DefaultDB = databaseConfig.db
}

/**
 * Represents grid FS data service.
 */
trait GridFSDataService extends DataService {
  lazy val gridFS = GridFS(db)
}

/**
 * Represents the service which works with a collection
 */
trait DataCollectionService extends DataService {
  private[services] val collectionName: String

  private[services] lazy val collection: BSONCollection = getCollection

  private[services] def getCollection = {
    val coll = db.collection(collectionName)
    onCollectionInitialized(coll)
    coll
  }

  protected def onCollectionInitialized(collection: BSONCollection) = {}
}

trait WithDbCommand extends DataService {
  def dbCommand[A](command: Command[A], readPreference: ReadPreference = ReadPreference.primary)(implicit ec: ExecutionContext) = db.command(command) 
}

trait WithExternalCollectionAccess extends DataService {
  protected def collection(collectionName: String) = db.collection(collectionName)
}

/**
 * Represents the document handler.
 */
trait DataDocumentHandler[A] {
  implicit val documentHandler: BSONDocumentReader[A] with BSONDocumentWriter[A] with BSONHandler[BSONDocument, A]
}

/**
 * Represents the read functionality of Crud service.
 */
trait DataReadService[A] {
  def findQuery(query: BSONDocument): GenericQueryBuilder[BSONDocument, BSONDocumentReader, BSONDocumentWriter]

  def findAll() = find(BSONDocument())

  def findOne(query: BSONDocument): Future[Option[A]]

  def findById(id: BSONObjectID): Future[Option[A]]

  def find(query: BSONDocument): Future[List[A]]

  def find(query: BSONDocument, sort: BSONDocument): Future[List[A]]

  def count(query: BSONDocument): Future[Int]

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[A]]
  
  def find(query: BSONDocument, sortOptions: SortOptions): Future[Seq[A]]
}

/**
 * Represents the remove functionality of the Crud service.
 */
trait DataRemoveService {
  def remove(id: BSONObjectID): Future[LastError] = remove(BSONDocument("_id" -> id))
  def remove(query: BSONDocument): Future[LastError]
}

/**
 * Represents the create or update functionality of the Crud service.
 */
trait DataSaveService[A] {
  def insert(model: A): Future[BSONObjectID]
  def save(model: A): Future[BSONObjectID]
}

/**
 * Represents the Read service implementation
 */
trait DataReadServiceImpl[A] extends DataCollectionService
  with DataDocumentHandler[A]
  with DataReadService[A] {

  def findQuery(query: BSONDocument): GenericQueryBuilder[BSONDocument, BSONDocumentReader, BSONDocumentWriter] = collection.find(query)

  def findById(id: BSONObjectID): Future[Option[A]] = collection.find(BSONDocument("_id" -> id)).cursor[A].headOption

  def find(query: BSONDocument): Future[List[A]] = collection.find(query).cursor[A].collect[List]()

  def find(query: BSONDocument, sort: BSONDocument): Future[List[A]] = collection.find(query).sort(sort).cursor[A].collect[List]()

  def findOne(query: BSONDocument): Future[Option[A]] = collection.find(query).one[A]

  def count(query: BSONDocument): Future[Int] = db.command(Count(collectionName, Some(query)))
  
  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[A]] = {
    val sortDocs = applyDefaultSort(queryOptions.sortInfos) map { sortInfo =>
      (sortInfo.field -> BSONInteger(sortInfo.direction))
    }

    val queryBuilder = collection.find(query)
      .options(QueryOpts(queryOptions.startRowIndex, queryOptions.pageSize))
      .sort(BSONDocument(sortDocs))

    for {
      data <- queryBuilder.cursor[A].collect[List](queryOptions.pageSize)
      totalCount <- count(query)
    } yield DataWithTotalCount(data, totalCount)
  }

  def find(query: BSONDocument, sortOptions: SortOptions): Future[List[A]] = {
    val sortDocs = applyDefaultSort(sortOptions.sortInfos) map { sortInfo =>
      (sortInfo.field -> BSONInteger(sortInfo.direction))
    }

    collection.find(query)
      .sort(BSONDocument(sortDocs))
      .cursor[A]
      .collect[List]()
  }

  protected def applyDefaultSort(sortInfos: List[SortInfo]): List[SortInfo] = sortInfos match {
    case Nil => defaultSort
    case _ => sortInfos
  }

  protected def defaultSort: List[SortInfo] = Nil
}

/**
 * Represents the CRUD service.
 */
trait DataCrudService[A] extends DataCollectionService
  with DataDocumentHandler[A]
  with DataReadServiceImpl[A]
  with DataRemoveService
  with DataSaveService[A] {

  def remove(query: BSONDocument): Future[LastError] = collection.remove(query)

  def insert(model: A): Future[BSONObjectID] = {
    val (newDoc, objectId) = getDocWithId(model)

    collection.insert(setCreateAndUpdateTime(newDoc)).map(_ => objectId)
  }

  def save(model: A): Future[BSONObjectID] = {
    val (newDoc, objectId) = getDocWithId(model)

    collection.save(setCreateAndUpdateTime(newDoc)).map(_ => objectId)
  }
  
  def update(selector: BSONDocument, update: BSONDocument, upsert: Boolean = false, multi: Boolean = false, setUpdateTime: Boolean = true): Future[LastError] = {
    val finalUpdateDoc = setUpdateTime match {
      case false => update
      case true => 
        val updateTime = DateTime.now
        update ++ BSONDocument("$set" -> BSONDocument(DataCrudService.UpdateTimeField -> new BSONDateTime(updateTime.getMillis)))
    }
    
    collection.update(selector, finalUpdateDoc, upsert = upsert, multi = multi)
  }
  
  private def getDocWithId(model: A) = {
    val fieldName = "_id"
    val doc = documentHandler.write(model)
    val objectId = doc.getAs[BSONObjectID](fieldName).getOrElse(BSONObjectID.generate)
    val newDoc = BSONDocument(doc.elements.filter(_._1 != fieldName) :+ (fieldName -> objectId))
    (newDoc, objectId)
  }
  
  private def setCreateAndUpdateTime(doc: BSONDocument) = {
    // Set create time if it wasn't available
    val createTime = doc.getAs[DateTime](DataCrudService.CreateTimeField).getOrElse(DateTime.now)
    val docWithCreateTime = BSONDocument(doc.elements.filter(_._1 != DataCrudService.CreateTimeField) :+ (DataCrudService.CreateTimeField -> new BSONDateTime(createTime.getMillis)))
    
    // Always set update time
    setUpdateTime(docWithCreateTime)
  }
  
  private def setUpdateTime(doc: BSONDocument) = {
    val updateTime = DateTime.now
    BSONDocument(doc.elements.filter(_._1 != DataCrudService.UpdateTimeField) :+ (DataCrudService.UpdateTimeField -> new BSONDateTime(updateTime.getMillis)))    
  }
}

object DataCrudService {
  val UpdateTimeField = "updateTime"
  val CreateTimeField = "createTime"
}
package xcala.play.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.bson._
import reactivemongo.api._
import reactivemongo.core.commands._
import reactivemongo.api.collections.default._
import reactivemongo.api.collections._
import play.api.Play
import play.api.libs.iteratee.Enumerator
import xcala.play.models._
import xcala.play.extensions.Handlers._
import reactivemongo.api.gridfs.GridFS

/**
 * Represents the data service foundation.
 */
trait DataService {
  private[services] lazy val driver: MongoDriver = DataService.driver
  private[services] lazy val connection: MongoConnection = DataService.connection
  private[services] lazy val db: DefaultDB = DataService.db
}

trait GridFSDataService extends DataService {
  lazy val gridFS = GridFS(db)
}

object DataService {
  lazy val mongoUri = Play.current.configuration.getString("mongodb.uri").get
  lazy val parsedUri: MongoConnection.ParsedURI = MongoConnection.parseURI(mongoUri).get

  lazy val driver: MongoDriver = new MongoDriver
  lazy val connection: MongoConnection = driver.connection(parsedUri)
  lazy val db: DefaultDB = connection.db(parsedUri.db.get)
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

  protected def applyDefaultSort(sortInfos: List[SortInfo]): List[SortInfo] = sortInfos match {
    case Nil => defaultSort
    case _ => sortInfos
  }

  protected def defaultSort: List[SortInfo] = Nil
}

/**
 * Represents the Crud service.
 */
trait DataCrudService[A] extends DataCollectionService
  with DataDocumentHandler[A]
  with DataReadServiceImpl[A]
  with DataRemoveService
  with DataSaveService[A] {

  def remove(query: BSONDocument): Future[LastError] = collection.remove(query)

  def insert(model: A): Future[BSONObjectID] = {
    val doc = documentHandler.write(model)
    val objectId = doc.getAs[BSONObjectID]("_id").getOrElse(BSONObjectID.generate)
    val newDoc = BSONDocument(doc.elements.filter(_._1 != "_id") :+ ("_id" -> objectId))

    collection.insert(newDoc).map(_ => objectId)
  }

  def save(model: A): Future[BSONObjectID] = {
    val doc = documentHandler.write(model)
    val objectId = doc.getAs[BSONObjectID]("_id").getOrElse(BSONObjectID.generate)
    val newDoc = BSONDocument(doc.elements.filter(_._1 != "_id") :+ ("_id" -> objectId))

    collection.save(newDoc).map(_ => objectId)
  }

  def update(selector: BSONDocument, update: BSONDocument, upsert: Boolean = false, multi: Boolean = false): Future[LastError] = {
    collection.update(selector, update, upsert = upsert, multi = multi)
  }
}
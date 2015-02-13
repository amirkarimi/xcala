package xcala.play.services

import xcala.play.models._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import reactivemongo.core.commands.LastError
import reactivemongo.bson.Macros
import reactivemongo.bson._
import play.Logger
import reactivemongo.api.indexes._
import xcala.play.extensions.BSONHandlers._
import org.joda.time.DateTime
import reactivemongo.api.collections.default.BSONCollection

class IndexedItemService(implicit val ec: ExecutionContext) extends DataCrudService[IndexedItem] {
  val collectionName = "indexedItems"
  val documentHandler = Macros.handler[IndexedItem]

  override def onCollectionInitialized(collection: BSONCollection) = {
    collection.indexesManager.ensure(Index(Seq("itemType" -> IndexType.Ascending, 
                        "lang" -> IndexType.Ascending, 
                        "title" -> IndexType.Ascending, 
                        "content" -> IndexType.Ascending), unique = false))    
  }
}
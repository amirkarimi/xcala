package xcala.play.services

import xcala.play.models._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import reactivemongo.core.commands.LastError
import reactivemongo.bson.Macros
import reactivemongo.bson._
import play.Logger
import reactivemongo.api.indexes._
import xcala.play.extensions.Handlers._
import org.joda.time.DateTime

trait IndexableService[A <: Indexable] extends DataService with DataCollectionService with DataRemoveService with DataSaveService[A] with DataDocumentHandler[A] {
  implicit val indexedItemHandler = Macros.handler[IndexedItem]
  
  lazy val indexedItemCollection = {    
    val coll = db.collection("indexedItems")    
    collection.indexesManager.ensure(Index(Seq("itemType" -> IndexType.Ascending, "lang" -> IndexType.Ascending, "title" -> IndexType.Ascending, "content" -> IndexType.Ascending), unique = false))
    coll
  }
  
  abstract override def save(model: A): Future[BSONObjectID] = {
    val document = documentHandler.write(model)
    
    val result = super.save(model)
    
    result.map { objectId =>
      saveItem(objectId, model).recover {
        case err => Logger.error("Saving indexed item error: " + err.toString)        
      }      
    }
    
    result
  }
  
  private def saveItem(id: BSONObjectID, model: Indexable): Future[LastError] = {
    val existingItem = indexedItemCollection.find(BSONDocument("itemId" -> id, "itemType" -> model.itemType)).one[IndexedItem]
    val indexedItemFuture = existingItem.map(updateOrNewIndexedItem(_, id, model))
    indexedItemFuture flatMap { indexedItem =>
      indexedItemCollection.save(indexedItem)
    }
  }
  
  private def updateOrNewIndexedItem(indexedItem: Option[IndexedItem], id: BSONObjectID, model: Indexable) = {
    IndexedItem(
      id = indexedItem.flatMap(_.id),
      itemType = model.itemType,
      itemId = id,
      lang = model.lang,
      title = model.title,
      content = model.content,
      updateTime = DateTime.now
    )
  }
}
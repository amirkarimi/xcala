package xcala.play.services

import org.joda.time.DateTime
import play.api.Logger
import reactivemongo.api.bson.Macros
import reactivemongo.api.bson._
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes._
import xcala.play.models.Indexable
import xcala.play.models.IndexedItem
import xcala.play.extensions.BSONHandlers._

import scala.concurrent.Future
import play.api.Logging

trait IndexableService[A <: Indexable]
    extends DataService
    with DataCollectionService
    with DataRemoveService
    with DataSaveService[A]
    with DataDocumentHandler[A]
    with Logging {
  implicit val indexedItemHandler = Macros.handler[IndexedItem]

  lazy val indexedItemCollection = {
    val coll = dbFuture.map(_.collection("indexedItems"))
    collectionFuture.map(
      _.indexesManager.ensure(
        Index(
          Seq(
            "itemType" -> IndexType.Ascending,
            "lang"     -> IndexType.Ascending,
            "title"    -> IndexType.Ascending,
            "content"  -> IndexType.Ascending
          ),
          unique = false
        )
      )
    )

    coll
  }

  abstract override def save(model: A): Future[BSONObjectID] = {
    val document = documentHandler.writeTry(model).get

    val result = super.save(model)

    result.map { objectId =>
      saveItem(objectId, model).recover { case err =>
        logger.error("Saving indexed item error: " + err.toString)
      }
    }

    result
  }

  private def saveItem(id: BSONObjectID, model: Indexable): Future[WriteResult] = {
    val existingItem =
      indexedItemCollection.flatMap(_.find(BSONDocument("itemId" -> id, "itemType" -> model.itemType)).one[IndexedItem])
    val indexedItemFuture = existingItem.map(updateOrNewIndexedItem(_, id, model))
    indexedItemFuture.flatMap { indexedItem =>
      indexedItemCollection.flatMap(_.update.one(BSONDocument("_id" -> indexedItem.id), indexedItem, upsert = true))
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

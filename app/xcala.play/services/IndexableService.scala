package xcala.play.services

import xcala.play.extensions.BSONHandlers._
import xcala.play.models.Indexable
import xcala.play.models.IndexedItem

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import org.joda.time.DateTime
import reactivemongo.api.bson._
import reactivemongo.api.bson.Macros
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes._

trait IndexableService[A <: Indexable]
    extends DataService
    with DataCollectionService
    with DataRemoveService
    with DataSaveService[A]
    with DataDocumentHandler[A] {
  implicit val indexedItemHandler: BSONDocumentHandler[IndexedItem] = Macros.handler[IndexedItem]

  lazy val indexedItemCollection: Future[BSONCollection] = {
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
    documentHandler.writeTry(model) match {
      case Success(_) =>
        val result = super.save(model)

        result.map { objectId =>
          saveItem(objectId, model)
        }

        result
      case Failure(e) =>
        Future.failed(e)
    }
  }

  abstract override def remove(id: BSONObjectID): Future[WriteResult] =
    super.remove(id).flatMap { _ =>
      removeItem(itemId = id)
    }

  private def saveItem(id: BSONObjectID, model: Indexable): Future[WriteResult] = {
    val existingItem =
      indexedItemCollection.flatMap(_.find(BSONDocument("itemId" -> id, "itemType" -> model.itemType)).one[IndexedItem])
    val indexedItemFuture = existingItem.map(updateOrNewIndexedItem(_, id, model))
    indexedItemFuture.flatMap { indexedItem =>
      indexedItemCollection.flatMap(_.update.one(BSONDocument("_id" -> indexedItem.id), indexedItem, upsert = true))
    }
  }

  protected def removeItem(itemId: BSONObjectID): Future[WriteResult] =
    indexedItemCollection
      .flatMap { collection =>
        collection.delete.one(BSONDocument("itemId" -> itemId))
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

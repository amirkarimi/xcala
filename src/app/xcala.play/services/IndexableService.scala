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
import xcala.play.utils.WithExecutionContext
import reactivemongo.api.collections._
import reactivemongo.api.collections.default._

trait IndexableService[A <: Indexable]
  extends WithExecutionContext
  with DataReadService[A]
  with DataRemoveService
  with DataSaveService[A] {

  val indexedItemService: IndexedItemService

  abstract override def insert(model: A): Future[BSONObjectID] = {
    val result = super.insert(model)

    result.map { objectId =>
      saveItem(objectId, model).recover {
        case err => Logger.error("Inserting indexed item error: " + err.toString)
      }
    }

    result
  }

  abstract override def save(model: A): Future[BSONObjectID] = {
    val result = super.save(model)

    result.map { objectId =>
      saveItem(objectId, model).recover {
        case err => Logger.error("Saving indexed item error: " + err.toString)
      }
    }

    result
  }

  abstract override def remove(query: BSONDocument): Future[LastError] = {
    for {
      models <- find(query)
      modelIds = models.map(_.id.get)
      indexedItemRemoveError <- indexedItemService.remove(BSONDocument("itemId" -> BSONDocument("$in" -> modelIds)))
      removeError <- super.remove(query)
    } yield {
      removeError
    }
  }

  private def saveItem(id: BSONObjectID, model: Indexable): Future[BSONObjectID] = {
    val existingItem = indexedItemService.findOne(BSONDocument("itemId" -> id, "itemType" -> model.itemType))
    val indexedItemFuture = existingItem.map(updateOrNewIndexedItem(_, id, model))
    indexedItemFuture flatMap { indexedItem =>
      indexedItemService.save(indexedItem)
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
      updateTime = DateTime.now)
  }
}
package xcala.play.services

import reactivemongo.api.bson.{BSONDocument, BSONObjectID}
import reactivemongo.api.commands.WriteResult
import scala.concurrent.Future

trait WithSafeDelete extends DataCollectionService with DataRemoveService {
  /**
    * Specifies the list of tuples containing the collection name and a function to build a query on the specified
    * collection that will prevent from deleting the entity if the query matched
    * @return
    */
  def checkOnDelete: Seq[(String, BSONObjectID => BSONDocument)]

  abstract override def remove(query: BSONDocument): Future[WriteResult] = {
    val deletingIdsFuture = collectionFuture flatMap { collection =>
      collection.find(query, Some(BSONDocument("_id" -> 1)))
        .cursor[BSONDocument]()
        .collect[List]()
        .map(_.flatMap(_.getAsOpt[BSONObjectID]("_id")))
    }

    deletingIdsFuture flatMap { deletingIds =>
      val checkFuture = Future.sequence(
        deletingIds flatMap { deletingId =>
          checkOnDelete map { case (collectionName, queryBuilder) =>
            val query = queryBuilder(deletingId)
            dbFuture flatMap { db =>
              val collection = db.collection(collectionName)
              collection.count(Some(query)) map { count =>
                if (count > 0) throw new DeleteConstraintException(collectionName, query, deletingId)
              }
            }
          }
        }
      )

      checkFuture flatMap { _ =>
        super.remove(query)
      }
    }
  }
}

class DeleteConstraintException(val collectionName: String, val query: BSONDocument, val id: BSONObjectID) extends Throwable

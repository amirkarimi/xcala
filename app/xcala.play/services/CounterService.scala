package xcala.play.services

import xcala.play.utils.WithExecutionContext

import scala.concurrent.Future

import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult

trait CounterService extends DataService with WithExecutionContext {

  protected def counterCollectionName: String
  protected lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(_.collection(counterCollectionName))

  def increment(key: String): Future[WriteResult] = {
    collectionFuture.flatMap(
      _.update.one(BSONDocument("key" -> key), BSONDocument("$inc" -> BSONDocument("count" -> 1)), upsert = true)
    )
  }

}

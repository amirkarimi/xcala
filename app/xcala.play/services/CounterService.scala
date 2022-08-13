package xcala.play.services

import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult
import xcala.play.utils.WithExecutionContext

import scala.concurrent.Future

trait CounterService extends DataService with WithExecutionContext {

  protected def counterCollectionName: String
  protected lazy val collectionFuture: Future[BSONCollection] = dbFuture.map(_.collection(counterCollectionName))

  def increment(key: String): Future[WriteResult] = {
    collectionFuture.flatMap(
      _.update.one(BSONDocument("key" -> key), BSONDocument("$inc" -> BSONDocument("count" -> 1)), upsert = true)
    )
  }

}

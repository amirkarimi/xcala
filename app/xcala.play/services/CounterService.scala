package xcala.play.services

import reactivemongo.api.bson._
import xcala.play.utils.WithExecutionContext

trait CounterService extends DataService with WithExecutionContext {

  protected def counterCollectionName: String
  protected lazy val collectionFuture = dbFuture.map(_.collection(counterCollectionName))

  def increment(key: String) = {
    collectionFuture.flatMap(
      _.update.one(BSONDocument("key" -> key), BSONDocument("$inc" -> BSONDocument("count" -> 1)), upsert = true)
    )
  }

}

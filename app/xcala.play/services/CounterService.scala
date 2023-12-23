package xcala.play.services

import xcala.play.utils.WithExecutionContext

import scala.concurrent.Future

import reactivemongo.api.bson._
import reactivemongo.api.commands.WriteResult

trait CounterService extends DataCollectionService with WithExecutionContext {

  def increment(key: String): Future[WriteResult] = {
    collectionFuture.flatMap(
      _.update.one(
        BSONDocument("key"  -> key),
        BSONDocument("$inc" -> BSONDocument("count" -> 1)),
        upsert = true
      )
    )
  }

}

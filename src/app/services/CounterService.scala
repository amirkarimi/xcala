package xcala.play.services

import concurrent.ExecutionContext.Implicits._
import reactivemongo.bson._

trait CounterService extends DataService {
  protected def counterCollectionName: String
  protected lazy val collection = db.collection(counterCollectionName)
  
  def increment(key: String) = {
    collection.update(
      BSONDocument("key" -> key),
      BSONDocument("$inc" -> BSONDocument("count" -> 1)),
      upsert = true)
  }
}
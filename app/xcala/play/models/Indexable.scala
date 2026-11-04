package xcala.play.models

import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

abstract class Indexable(val lastUpdateTime: DateTime) { this: DocumentWithId =>
  val id: Option[BSONObjectID]
  def itemType: String
  def lang    : String
  def title   : String
  def content : String
}

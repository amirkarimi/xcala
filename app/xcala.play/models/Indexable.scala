package xcala.play.models

import reactivemongo.api.bson.BSONObjectID

trait Indexable {
  val id: Option[BSONObjectID]
  def itemType: String
  def lang: String
  def title: String
  def content: String
}

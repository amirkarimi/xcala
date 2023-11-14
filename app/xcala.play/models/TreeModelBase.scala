package xcala.play.models

import reactivemongo.api.bson.BSONObjectID

trait TreeModelBase[A <: TreeModelBase[_]] { this: DocumentWithId =>
  def id          : Option[BSONObjectID]
  def parentId    : Option[BSONObjectID]
  def generalTitle: String
  def children    : List[A]
}

package xcala.play.models

import reactivemongo.api.bson.BSONObjectID

trait DocumentWithId {
  def id: Option[BSONObjectID]
}

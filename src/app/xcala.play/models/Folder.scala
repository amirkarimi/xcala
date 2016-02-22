package xcala.play.models

import reactivemongo.bson.Macros.Annotations.Key
import reactivemongo.bson._

case class Folder(
  @Key("_id") id: Option[BSONObjectID],
  name: String,
  parent: Option[BSONObjectID])
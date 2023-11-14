package xcala.play.models

import reactivemongo.api.bson._
import reactivemongo.api.bson.Macros.Annotations.Key

final case class Folder(
    @Key("_id") id: Option[BSONObjectID],
    name          : String,
    parent        : Option[BSONObjectID]
) extends DocumentWithId

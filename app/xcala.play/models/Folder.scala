package xcala.play.models

import reactivemongo.api.bson.Macros.Annotations.Key
import reactivemongo.api.bson._

final case class Folder(
    @Key("_id") id: Option[BSONObjectID],
    name: String,
    parent: Option[BSONObjectID]
)

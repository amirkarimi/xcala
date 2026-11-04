package xcala.play.models

import org.joda.time.DateTime
import reactivemongo.api.bson._
import reactivemongo.api.bson.Macros.Annotations.Key

final case class Task(
    @Key("_id")
    id            : Option[BSONObjectID],
    status        : TaskStatus.Type,
    ownerType     : TaskOwner.Type,
    ownerId       : BSONObjectID,
    result        : Option[String],
    taskLabel     : String,
    lastUpdateTime: DateTime,
    createTime    : DateTime
) extends DocumentWithId

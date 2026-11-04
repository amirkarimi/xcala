package xcala.play.models

import org.joda.time.DateTime
import reactivemongo.api.bson._
import reactivemongo.api.bson.Macros.Annotations.Key

/** The lock of `label`, held by the task `taskId` since `lockedAt`. The label is the `_id`, so there is at most one lock
  * per label, and at most one task per label runs at a time.
  */
final case class TaskLock(
    @Key("_id")
    label   : String,
    taskId  : BSONObjectID,
    lockedAt: DateTime
)

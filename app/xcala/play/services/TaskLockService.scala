package xcala.play.services

import xcala.play.extensions.BSONHandlers._
import xcala.play.models._
import xcala.play.utils.IO
import xcala.play.utils.IO.syntax._

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.joda.time.DateTime
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection.BSONCollection
import reactivemongo.api.commands.WriteResult

/** One [[TaskLock]] per label (collection `taskLocks`), so at most one task per label runs at a time. MongoDB's unique
  * `_id` makes taking a lock atomic, across requests and servers, with no index to create first. A lock taken before
  * `staleBefore` is considered abandoned (its server most likely restarted) and can be taken over.
  */
@Singleton
class TaskLockService @Inject() (databaseConfig: DefaultDatabaseConfig)(implicit ec: ExecutionContext) {
  import TaskLockService._

  private lazy val locks: Future[BSONCollection] = databaseConfig.databaseFuture.map(_.collection(CollectionName))

  /** Takes the lock of `label` for `taskId`; false if another task holds it and took it after `staleBefore`. */
  def acquire(label: String, taskId: BSONObjectID, now: DateTime, staleBefore: DateTime): IO[Nothing, Boolean] = {
    def takeOverIfStale: Future[Boolean] =
      locks
        .flatMap(
          _.findAndUpdate(
            selector = BSONDocument("_id" -> label, LockedAt -> BSONDocument("$lt" -> staleBefore)),
            update   = BSONDocument("$set" -> BSONDocument(TaskId -> taskId, LockedAt -> now))
          )
        )
        .map(_.value.isDefined)

    locks
      .flatMap(_.insert.one(TaskLock(label, taskId, now)))
      .map(_ => true)
      .recoverWith { case WriteResult.Code(DuplicateKeyCode) => takeOverIfStale }
      .toIO
  }

  /** The lock of `label`, if it was taken after `staleBefore`. */
  def find(label: String, staleBefore: DateTime): IO[Nothing, Option[TaskLock]] =
    locks
      .flatMap(_.find(BSONDocument("_id" -> label, LockedAt -> BSONDocument("$gte" -> staleBefore))).one[TaskLock])
      .toIO

  /** The task holding the lock of `label`, if it took the lock after `staleBefore`. */
  def holder(label: String, staleBefore: DateTime): IO[Nothing, Option[BSONObjectID]] =
    find(label, staleBefore).map(_.map(_.taskId))

  /** Whether some task holds the lock of `label`, taken after `staleBefore`. */
  def isHeld(label: String, staleBefore: DateTime): IO[Nothing, Boolean] =
    find(label, staleBefore).map(_.isDefined)

  /** Frees the lock of `label` if `taskId` holds it. */
  def release(label: String, taskId: BSONObjectID): IO[Nothing, Unit] =
    locks.flatMap(_.delete.one(BSONDocument("_id" -> label, TaskId -> taskId))).map(_ => ()).toIO

}

object TaskLockService {
  val CollectionName: String = "taskLocks"

  implicit val documentHandler: BSONDocumentHandler[TaskLock] = Macros.handler[TaskLock]

  private val TaskId          : String = "taskId"
  private val LockedAt        : String = "lockedAt"
  private val DuplicateKeyCode: Int    = 11000
}

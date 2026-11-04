package xcala.play.services

import xcala.play.extensions.BSONHandlers._
import xcala.play.models._

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.joda.time.DateTime
import reactivemongo.api.bson._

@Singleton
class TaskService @Inject() (
    val databaseConfig: DefaultDatabaseConfig,
    implicit val ec   : ExecutionContext
) extends DataSaveServiceImpl[Task] with DataReadSimpleServiceImpl[Task] {
  val collectionName : String                    = "tasks"
  val documentHandler: BSONDocumentHandler[Task] = Macros.handler[Task]

  def createNewTask(
      ownerType: TaskOwner.Type,
      ownerId  : BSONObjectID,
      taskLabel: String
  ): Future[BSONObjectID] =
    insert(
      Task(
        id             = None,
        status         = TaskStatus.Running,
        ownerType      = ownerType,
        ownerId        = ownerId,
        result         = None,
        lastUpdateTime = DateTime.now(),
        createTime     = DateTime.now(),
        taskLabel      = taskLabel
      )
    )

  def markTaskAsFinished(
      taskResult: String,
      taskId    : BSONObjectID,
      ownerType : TaskOwner.Type,
      ownerId   : BSONObjectID
  ): Future[_] =
    update(
      selector = BSONDocument(
        "_id"       -> taskId,
        "ownerType" -> ownerType,
        "ownerId"   -> ownerId
      ),
      update =
        BSONDocument(
          "$set" ->
            BSONDocument(
              "status"         -> TaskStatus.Finished,
              "result"         -> taskResult,
              "lastUpdateTime" -> DateTime.now()
            )
        ),
      setUpdateTime = false
    )

  def getTaskStatus(
      taskId   : BSONObjectID,
      ownerType: TaskOwner.Type,
      ownerId  : BSONObjectID
  ): Future[Option[TaskStatus.Type]] = {
    findOne(
      BSONDocument(
        "_id"       -> taskId,
        "ownerType" -> ownerType,
        "ownerId"   -> ownerId
      )
    ).map(_.map(_.status))
  }

  def getTaskResult(
      taskId   : BSONObjectID,
      ownerType: TaskOwner.Type,
      ownerId  : BSONObjectID
  ): Future[Option[String]] =
    findOne(
      BSONDocument(
        "_id"       -> taskId,
        "ownerType" -> ownerType,
        "ownerId"   -> ownerId
      )
    ).map(_.flatMap(_.result))

}

package xcala.play.services

import scala.concurrent.ExecutionContext
import xcala.play.services._
import xcala.play.models.Folder
import reactivemongo.bson.Macros
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.LastError
import scala.concurrent.Future

class FolderService(implicit val ec: ExecutionContext) extends DataCrudService[Folder] {
  val collectionName = "folders"
  val documentHandler = Macros.handler[Folder]  
}
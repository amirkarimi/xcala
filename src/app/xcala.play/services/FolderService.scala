package xcala.play.services

import scala.concurrent.ExecutionContext.Implicits._
import xcala.play.services._
import xcala.play.models.Folder
import reactivemongo.bson.Macros
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.LastError
import scala.concurrent.Future

class FolderService extends DataCrudService[Folder] {
  val collectionName = "folders"
  val documentHandler = Macros.handler[Folder]  
}
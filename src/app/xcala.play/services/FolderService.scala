package xcala.play.services

import play.api.Configuration

import scala.concurrent.ExecutionContext
import xcala.play.models.Folder
import reactivemongo.api.bson._

class FolderService(implicit val ec: ExecutionContext, val configuration: Configuration) extends DataCrudService[Folder] {
  val collectionName = "folders"
  val documentHandler = Macros.handler[Folder]  
}
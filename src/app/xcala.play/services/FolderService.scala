package xcala.play.services

import scala.concurrent.ExecutionContext
import xcala.play.models.Folder
import reactivemongo.api.bson._

class FolderService(implicit val ec: ExecutionContext) extends DataCrudService[Folder] {
  val collectionName = "folders"
  val documentHandler = Macros.handler[Folder]  
}
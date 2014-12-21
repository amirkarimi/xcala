package xcala.play.services

import xcala.play.services._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import reactivemongo.bson._
import reactivemongo.api.gridfs._
import reactivemongo.core.commands.LastError
import xcala.play.models._
import xcala.play.models.FileEntry.BSONDocumentReader

class FileService(folderService: FolderService) extends GridFSDataService {
  implicit val folderDocumentHandler = Macros.handler[Folder]
  
  def setFileFolder(file: ReadFile[BSONValue], folderId: Option[BSONObjectID]): Future[Option[LastError]] = folderId match {
    case None => Future.successful(None)
    case Some(folderId) => 
      gridFS.files.update(
        BSONDocument("_id" -> file.id), 
        BSONDocument("$set" -> BSONDocument("folderId" -> folderId))).map(Some(_))
  }
  
  def setFileAsHidden(file: ReadFile[BSONValue]): Future[Option[LastError]] = {
    gridFS.files.update(
      BSONDocument("_id" -> file.id), 
      BSONDocument("$set" -> BSONDocument("isHidden" -> true))).map(Some(_))
  }
  
  def createFolder(folder: Folder) = folderService.save(folder)
  
  def getFile(fileId: BSONObjectID): Future[Option[FileEntry]] = {
    gridFS.files.find(BSONDocument("_id" -> fileId)).one[FileEntry]
  }
  
  def getFilesUnderFolder(folderId: Option[BSONObjectID], fileType: Option[String] = None): Future[List[FileEntry]] = {    
    gridFS.find(
      BSONDocument("$and" -> 
        List(
          BSONDocument("isHidden" -> BSONDocument("$exists" -> false)),
          BSONDocument("folderId" -> BSONDocument("$exists" -> folderId.isDefined)),
	        BSONDocument("folderId" -> folderId),
	        BSONDocument("contentType" -> fileType.map(BSONRegex(_, "i")))
        )
      )
    ).collect[List]()
  }
  
  def getFoldersUnderFolder(folderId: Option[BSONObjectID]): Future[List[Folder]] = {
    folderService.find(      
      BSONDocument("$and" -> List(
        BSONDocument("parent" -> BSONDocument("$exists" -> folderId.isDefined)),
        BSONDocument("parent" -> folderId)
      )))
  }
  
  def getFolderAndParents(folderId: Option[BSONObjectID]): Future[List[Folder]] = {
    def getFoldersAndParents(folderId: Option[BSONObjectID], folders: List[Folder]): Future[List[Folder]] = {
	    folderId match {
	      case None => Future.successful(folders)
	      case Some(folderId) =>
		    	val folderFuture = folderService.findOne(BSONDocument("_id" -> folderId))
	        folderFuture flatMap { 
		    	  case None => Future.successful(folders)
		    	  case Some(folder) => 
		    	    val newFolders = folder :: folders
		    	    
		    	    folder.parent match {
			    	    case None => Future.successful(newFolders)
			    	    case Some(parentID) => getFoldersAndParents(Some(parentID), newFolders)
    	      	}
	    	  }
	    }
    }

    getFoldersAndParents(folderId, Nil)
  }
    
  def removeFile(id: BSONObjectID) = gridFS.remove(id)

  def removeFile(query: BSONDocument) = gridFS.files.remove(query)
  
  def renameFile(id: BSONObjectID, newName: String) = {
    gridFS.files.update(BSONDocument("_id" -> id), BSONDocument("$set" -> BSONDocument("filename" -> newName)))
  }
  
  def renameFolder(id: BSONObjectID, newName: String) = {
    folderService.collection.update(BSONDocument("_id" -> id), BSONDocument("$set" -> BSONDocument("name" -> newName)))
  }
  
  def removeFolder(id: BSONObjectID): Future[LastError] = removeFolder(BSONDocument("_id" -> id))
  
  def removeFolder(query: BSONDocument): Future[LastError] = {
    folderService.find(query) flatMap { deletingFolders =>      
      folderService.remove(query) flatMap { _ =>        
        val removeFutures = deletingFolders map { deletingFolder =>
          removeFile(BSONDocument("folderId" -> deletingFolder.id.get))
        }
        
        Future.reduce(removeFutures)((r, t) => t)
      }
    }
  }
}
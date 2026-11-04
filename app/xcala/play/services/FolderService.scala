package xcala.play.services

import xcala.play.models.FileInfo
import xcala.play.models.Folder
import xcala.play.services.DataRemoveServiceImpl
import xcala.play.services.DataSaveServiceImpl

import play.api.mvc.RequestHeader

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import reactivemongo.api.bson.{BSONDocument, BSONDocumentHandler, BSONObjectID, Macros}
import reactivemongo.api.commands.WriteResult

@Singleton
class FolderService @Inject() (
    fileInfoService   : FileInfoService,
    val databaseConfig: DefaultDatabaseConfig,
    implicit val ec   : ExecutionContext
) extends DataReadSimpleServiceImpl[Folder]
    with DataSaveServiceImpl[Folder]
    with DataRemoveServiceImpl[Folder] {
  val collectionName : String                      = "folders"
  val documentHandler: BSONDocumentHandler[Folder] = Macros.handler[Folder]

  def getFoldersUnderFolder(folderId: Option[BSONObjectID]): Future[Seq[Folder]] = {
    find(
      BSONDocument(
        "$and" ->
          List(
            BSONDocument("parent" -> BSONDocument("$exists" -> folderId.isDefined)),
            BSONDocument("parent" -> folderId)
          )
      )
    )
  }

  def removeFolder(id: BSONObjectID)(implicit
      requestHeader: RequestHeader
  ): Future[Option[WriteResult]] = {
    getFilesUnderFolderRecursive(id).flatMap { files =>
      removeFolderUnderFolder(id).map { writeError =>
        if (!writeError.exists(_.writeErrors.nonEmpty)) {
          files.map(f => fileInfoService.removeFile(f.id.get))
        }

        writeError.headOption
      }
    }
  }

  def getFilesUnderFolderRecursive(folderId: BSONObjectID): Future[Seq[FileInfo]] = {
    for {
      files   <- fileInfoService.getFilesUnderFolder(Some(folderId))
      folders <- getFoldersUnderFolder(Some(folderId))
      filesUnderFolders <-
        Future.sequence(folders.map(f => getFilesUnderFolderRecursive(f.id.get))).map(_.flatten)
    } yield {
      files ++ filesUnderFolders
    }
  }

  def getFoldersUnderFolderRecursive(folderId: BSONObjectID): Future[Seq[Folder]] = {
    for {
      folder  <- findById(folderId)
      folders <- getFoldersUnderFolder(Some(folderId))
      filesUnderFolders <-
        Future.sequence(folders.map(f => getFoldersUnderFolderRecursive(f.id.get))).map(_.flatten)
    } yield {
      folder.toSeq ++ folders ++ filesUnderFolders
    }
  }

  def getFolderAndParents(folderId: Option[BSONObjectID]): Future[List[Folder]] = {
    def getFoldersAndParents(folderId: Option[BSONObjectID], folders: List[Folder]): Future[List[Folder]] = {
      folderId match {
        case None => Future.successful(folders)
        case Some(folderId) =>
          val folderFuture = findById(folderId)
          folderFuture.flatMap {
            case None => Future.successful(folders)
            case Some(folder) =>
              val newFolders = folder :: folders

              folder.parent match {
                case None           => Future.successful(newFolders)
                case Some(parentId) => getFoldersAndParents(Some(parentId), newFolders)
              }
          }
      }
    }

    getFoldersAndParents(folderId, Nil)
  }

  def renameFolder(id: BSONObjectID, newName: String): Future[WriteResult] = {
    update(
      selector = BSONDocument("_id" -> id),
      update   = BSONDocument("$set" -> BSONDocument("name" -> newName))
    )
  }

  private def removeFolderUnderFolder(folderId: BSONObjectID): Future[Seq[WriteResult]] = {
    getFoldersUnderFolderRecursive(folderId).flatMap { folders =>
      Future.sequence(
        folders.map { folder =>
          super.remove(folder.id.get)
        }
      )
    }
  }

}

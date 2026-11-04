package xcala.play.postgres.services

import xcala.play.postgres.entities._
import xcala.play.postgres.models._
import xcala.play.postgres.services._

import play.api.mvc.RequestHeader

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FolderService @Inject() (
    fileInfoService    : FileInfoService,
    val dbConfig       : DbConfig,
    val tableDefinition: FolderTable,
    implicit val ec    : ExecutionContext
) extends DataQueryWithLongIdService[Folder]
    with DataRemoveServiceImpl[Long, Folder]
    with DataReadSimpleServiceImpl[Long, Folder]
    with DataSaveServiceImpl[Long, Folder] {
  import tableDefinition.profile.api._

  def getFoldersUnderFolder(folderId: Option[Long]): Future[Seq[Folder]] = {
    db.run(tableQuery.filter(f => f.parentId === folderId || (f.parentId.isEmpty && folderId.isEmpty)).result)
  }

  def getFolderAndParents(folderId: Option[Long]): Future[List[Folder]] = {
    def getFoldersAndParents(folderId: Option[Long], folders: List[Folder]): Future[List[Folder]] = {
      folderId match {
        case None => Future.successful(folders)
        case Some(folderId) =>
          val folderFuture = findById(folderId)
          folderFuture.flatMap {
            case None => Future.successful(folders)
            case Some(folder) =>
              val newFolders = folder :: folders

              folder.parentId match {
                case None           => Future.successful(newFolders)
                case Some(parentId) => getFoldersAndParents(Some(parentId), newFolders)
              }
          }
      }
    }

    getFoldersAndParents(folderId, Nil)
  }

  override def delete(id: Long)(implicit requestHeader: RequestHeader): Future[Int] = {
    getFilesUnderFolderRecursive(id).flatMap { files =>
      super.delete(id).map { result =>
        if (result == 1) {
          files.map(f => fileInfoService.delete(f.id.get))
        }
        result
      }
    }
  }

  def getFilesUnderFolderRecursive(folderId: Long): Future[Seq[FileInfo]] = {
    for {
      files   <- fileInfoService.getFilesUnderFolder(Some(folderId))
      folders <- getFoldersUnderFolder(Some(folderId))
      filesUnderFolders <-
        Future.sequence(folders.map(f => getFilesUnderFolderRecursive(f.id.get))).map(_.flatten)
    } yield {
      files ++ filesUnderFolders
    }
  }

  def renameFolder(id: Long, newName: String): Future[Int] = {
    db.run(tableQuery.filter(_.id === id).map(_.name).update(newName))
  }

}

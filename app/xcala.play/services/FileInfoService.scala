package xcala.play.services

import FileInfoService.FileObject
import s3.FileStorageService
import s3.FileStorageService.FileS3Object
import xcala.play.extensions.BSONHandlers._
import xcala.play.models.FileInfo

import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import reactivemongo.api.bson._
import reactivemongo.api.commands.WriteResult

object FileInfoService {

  final case class FileObject(
      id: BSONObjectID,
      name: String,
      content: InputStream,
      contentType: Option[String],
      contentLength: Option[Long]
  ) {

    def isImage: Boolean = contentType.exists(_.startsWith("image/"))
  }

}

@Singleton
class FileInfoService @Inject() (
    fileStorageService: FileStorageService,
    val databaseConfig: DefaultDatabaseConfig,
    implicit val ec: ExecutionContext
) extends DataCrudService[FileInfo] {

  val collectionName                                 = "files"
  val documentHandler: BSONDocumentHandler[FileInfo] = Macros.handler[FileInfo]

  def getFilesUnderFolder(folderId: Option[BSONObjectID], fileType: Option[String] = None): Future[Seq[FileInfo]] = {
    find(
      BSONDocument(
        "$and" ->
          List(
            BSONDocument("isHidden"    -> false),
            BSONDocument("folderId"    -> BSONDocument("$exists" -> folderId.isDefined)),
            BSONDocument("folderId"    -> folderId),
            BSONDocument("contentType" -> fileType.map(BSONRegex(_, "i")))
          )
      )
    )
  }

  def renameFile(id: BSONObjectID, newName: String): Future[Some[WriteResult]] = {
    update(BSONDocument("_id" -> id), BSONDocument("$set" -> BSONDocument("fileName" -> newName))).map(Some(_))
  }

  def removeFile(id: BSONObjectID): Future[Either[String, WriteResult]] = {
    fileStorageService.deleteByObjectName(id.stringify).flatMap {
      case true => remove(id).map(Right(_))
      case _    => Future.successful(Left("Storage problem"))
    }
  }

  def upload(fileInfo: FileInfo, content: Array[Byte]): Future[Either[String, BSONObjectID]] = {
    val id = BSONObjectID.generate
    fileStorageService
      .upload(
        objectName = id.stringify,
        content = content,
        contentType = fileInfo.contentType,
        originalName = fileInfo.name
      )
      .flatMap {
        case true =>
          insert(fileInfo.copy(id = Some(id))).map(Right.apply)
        case false =>
          Future(Left("Storage problem"))
      }
  }

  def findObjectById(id: BSONObjectID): Future[FileObject] = {
    fileStorageService.findByObjectName(id.stringify).transform {
      case Success(value) =>
        toFileObject(value)
      case Failure(exception) =>
        Failure(exception)
    }
  }

  private def toFileObject(fileS3Object: FileS3Object): Try[FileObject] =
    BSONObjectID.parse(fileS3Object.objectName).map { id =>
      FileObject(
        id = id,
        name = fileS3Object.originalName,
        content = fileS3Object.content,
        contentType = fileS3Object.contentType,
        contentLength = fileS3Object.contentLength
      )
    }

}

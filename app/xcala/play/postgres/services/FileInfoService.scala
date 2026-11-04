package xcala.play.postgres.services

import xcala.play.cross.services.CrossFileInfoService
import xcala.play.cross.services.s3.FileStorageService
import xcala.play.cross.services.s3.FileStorageService.FileS3Object
import xcala.play.postgres.entities._
import xcala.play.postgres.models._
import xcala.play.postgres.services.DataQueryWithUUIdService
import xcala.play.postgres.services.DataReadSimpleServiceImpl
import xcala.play.postgres.services.DataRemoveService
import xcala.play.postgres.services.DataSaveServiceImpl
import xcala.play.postgres.services.DbConfig
import xcala.play.postgres.services.FileInfoService.FileObject
import xcala.play.postgres.utils.QueryHelpers._

import play.api.mvc.RequestHeader

import java.io.InputStream
import java.util.UUID
import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.Using

object FileInfoService {

  final case class FileObject(
      id           : UUID,
      name         : String,
      content      : InputStream,
      contentType  : Option[String],
      contentLength: Option[Long]
  ) {
    def isImage: Boolean = contentType.exists(_.startsWith("image/"))
  }

}

@Singleton
class FileInfoService @Inject() (
    val dbConfig          : DbConfig,
    val tableDefinition   : FileTable,
    val fileStorageService: FileStorageService,
    implicit val ec       : ExecutionContext
) extends DataQueryWithUUIdService[FileInfo]
    with DataSaveServiceImpl[UUID, FileInfo]
    with DataReadSimpleServiceImpl[UUID, FileInfo]
    with DataRemoveService[UUID, FileInfo]
    with CrossFileInfoService[UUID, FileInfo] {

  import tableDefinition.profile.api._

  def getFilesUnderFolder(
      folderId   : Option[Long],
      contentType: Option[String] = None
  ): Future[Seq[FileInfo]] = {
    val query = tableQuery
      .filter { f =>
        !f.isHidden && (f.folderId === folderId || (f.folderId.isEmpty && folderId.isEmpty))
      }
      .optionalQuery { query =>
        contentType.map { contentType =>
          query.filter(_.contentType.like("%" + contentType + "%"))
        }
      }

    db.run(query.result)
  }

  def renameFile(id: UUID, newName: String): Future[Int] = {
    db.run(tableQuery.filter(_.id === id).map(_.name).update(newName))
  }

  def removeFile(id: UUID)(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Int]] = {
    fileStorageService.deleteByObjectName(id.toString).flatMap {
      case true =>
        val action = filterQueryById(id).delete
        db.run(action).map(Right(_))
      case _ => Future.successful(Left("Storage problem"))
    }
  }

  override def delete(id: UUID)(implicit
      requestHeader: RequestHeader
  ): Future[Int] =
    removeFile(id).map {
      case Right(value) => value
      case Left(_)      => 0
    }

  def upload(fileEntity: FileInfo, content: Array[Byte])(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, UUID]] = {
    val id: UUID = UUID.randomUUID()
    fileStorageService
      .upload(
        objectName   = id.toString,
        content      = content,
        contentType  = fileEntity.contentType,
        originalName = fileEntity.name
      )
      .flatMap {
        case true =>
          insert(fileEntity.copy(id = Some(id))).map(Right.apply)
        case _ =>
          Future(Left("Storage problem"))
      }
  }

  def findObjectById(id: UUID)(implicit
      requestHeader: RequestHeader
  ): Future[FileObject] = {
    fileStorageService.findByObjectName(id.toString).transform {
      case Success(value) =>
        toFileObject(value)
      case Failure(exception) =>
        Failure(exception)
    }
  }

  def duplicate(id: UUID)(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, UUID]] = {
    fileStorageService.findByObjectName(id.toString).transformWith {
      case Success(value) =>
        Future.fromTry(
          toFileObject(value)
            .map { fileObject =>
              findById(id).flatMap {
                case Some(fileInfo) =>
                  Future.fromTry(
                    Using(fileObject.content) { inputStream =>
                      upload(fileInfo, inputStream.readAllBytes())
                    }
                  ).flatten

                case None => Future.failed(new Throwable("file not found"))
              }
            }
        ).flatten

      case Failure(exception) =>
        Future.failed(exception)
    }
  }

  private def toFileObject(fileS3Object: FileS3Object): Try[FileObject] = {
    Try(UUID.fromString(fileS3Object.objectName)).map { id =>
      FileObject(
        id            = id,
        name          = fileS3Object.originalName,
        content       = fileS3Object.content,
        contentType   = fileS3Object.contentType,
        contentLength = fileS3Object.contentLength
      )
    }

  }

}

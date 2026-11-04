package xcala.play.controllers

import xcala.play.cross.controllers._
import xcala.play.cross.models._
import xcala.play.models.FileInfo
import xcala.play.services.FileInfoService

import play.api.libs.Files.TemporaryFile
import play.api.mvc._

import java.nio.file.Files.readAllBytes
import scala.concurrent.Future

import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

object WithFileUploader {
  val AutoUploadSuffix: String = "_autoupload"
}

trait WithFileUploader extends CrossWithFileUploader[BSONObjectID] {

  protected val fileInfoService: FileInfoService

  protected def handleObsoleteUploadedFiles(
      filesIds: Seq[String]
  )(implicit
      requestHeader: RequestHeader
  ): Future[Seq[Either[String, Int]]] =
    Future.traverse(filesIds.flatMap(BSONObjectID.parse(_).toOption))(fileInfoService.removeFile)

  protected def saveFile[A](
      filePart        : MultipartFormData.FilePart[TemporaryFile],
      maybeOldModel   : Option[A],
      handlePreResizes: Boolean
  )(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Option[BSONObjectID]]] = {
    val fileExtension = FilenameUtils.getExtension(filePart.filename)

    val fileInfo = FileInfo(
      name        = filePart.filename,
      extension   = fileExtension,
      contentType = filePart.contentType.getOrElse("unknown"),
      length      = filePart.ref.path.toFile.length,
      createTime  = DateTime.now,
      folderId    = None,
      isHidden    = true
    )

    val fileContentByteArray = readAllBytes(filePart.ref.path)
    fileInfoService.upload(fileInfo, fileContentByteArray).flatMap {
      case Right(fileId) =>
        if (handlePreResizes) {
          handleNewPreResizes(
            maybeOldModel = maybeOldModel.asInstanceOf[Option[PreResizedImageHolder[BSONObjectID]]],
            newFileId     = fileId
          ).map(_.map(_ => Some(fileId)))

        } else {
          Future.successful(Right(Some(fileId)))
        }

      case _ =>
        Future.successful(Right(None))
    }
  }

}

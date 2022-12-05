package xcala.play.controllers

import io.sentry.Sentry
import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import reactivemongo.api.bson.BSONObjectID
import xcala.play.models.FileInfo
import xcala.play.services.FileInfoService
import xcala.play.utils.{TikaMimeDetector, WithExecutionContext}

import java.nio.file.Files.readAllBytes
import scala.concurrent.Future
import scala.util.{Failure, Success}

object WithFileUploader {
  val AutoUploadSuffix = "_autoupload"
}

trait WithFileUploader extends WithExecutionContext {
  import WithFileUploader._

  protected val fileInfoService: FileInfoService

  private def filePartFormatChecks(tempFile: MultipartFormData.FilePart[TemporaryFile]): Boolean = {
    val contentAwareMimetype =
      TikaMimeDetector.guessMimeBasedOnFileContentAndName(tempFile.ref.path.toFile, tempFile.filename)
    tempFile.contentType.contains(contentAwareMimetype) &&
    (tempFile.contentType.contains("application/pdf") ||
    tempFile.contentType.exists(_.startsWith("image/")) ||
    tempFile.contentType.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
  }

  private def filePartFileLengthChecks(f: MultipartFormData.FilePart[TemporaryFile], maxLength: Option[Long]): Boolean =
    !maxLength.exists(_ < f.ref.path.toFile.length)

  private def filePartNameChecks(f: MultipartFormData.FilePart[TemporaryFile]): Boolean =
    f.key.endsWith("." + AutoUploadSuffix)

  def bindWithFiles[A](form: Form[A], maxLength: Option[Long] = None)(implicit
      messages: Messages,
      request: Request[MultipartFormData[TemporaryFile]]
  ): Future[Form[A]] = {
    val validFiles = request.body.files.filter { f =>
      filePartNameChecks(f) && filePartFileLengthChecks(f, maxLength) && filePartFormatChecks(f)
    }

    val formatErrors = request.body.files
      .filter { f =>
        filePartNameChecks(f) && !filePartFormatChecks(f)
      }
      .map { f =>
        FormError(f.key.dropRight(AutoUploadSuffix.length + 1), Messages("error.invalidFileFormat"))
      }

    val lengthErrors = request.body.files
      .filter { f =>
        filePartNameChecks(f) && !filePartFileLengthChecks(f, maxLength)
      }
      .map { f =>
        FormError(
          f.key.dropRight(AutoUploadSuffix.length + 1),
          Messages("error.fileToLarge", maxLength.get / 1024),
        )
      }

    val errors = formatErrors ++ lengthErrors

    val fileKeyValueFutures = validFiles.map { filePart =>
      val fieldName = filePart.key.dropRight(AutoUploadSuffix.length + 1)

      val removeOldFileFuture = form.data.get(fieldName) match {
        case Some(oldValue) =>
          BSONObjectID.parse(oldValue) match {
            case Failure(exception) =>
              Sentry.captureException(exception)
              Future.failed(exception)
            case Success(value) =>
              fileInfoService.removeFile(value).transformWith {
                case Failure(exception) =>
                  Sentry.captureException(exception)
                  Future.failed(exception)
                case Success(resultValue) =>
                  resultValue match {
                    case Left(errorMessage) =>
                      val exception = new Throwable(errorMessage)
                      Sentry.captureException(exception)
                      Future.failed(exception)
                    case Right(writeResult) =>
                      Future.successful(Some(writeResult))
                  }
              }
          }
        case None =>
          Future.successful(None)
      }

      removeOldFileFuture.flatMap { _ =>
        saveFile(filePart).map {
          case Some(fileId) => fieldName -> fileId.stringify
          case None         => ""        -> ""
        }
      }
    }

    Future.sequence(fileKeyValueFutures).map { fileKeyValues =>
      val newData = form.data ++ fileKeyValues.toMap

      errors match {
        case Nil => form.discardingErrors.bind(newData)
        case _   => form.discardingErrors.bind(newData).withError(errors.head)
      }
    }
  }

  private def saveFile(filePart: MultipartFormData.FilePart[TemporaryFile]): Future[Option[BSONObjectID]] = {
    val fileExtension = FilenameUtils.getExtension(filePart.filename)

    val fileInfo = FileInfo(
      name = filePart.filename,
      extension = fileExtension,
      contentType = filePart.contentType.getOrElse("unknown"),
      length = filePart.ref.path.toFile.length,
      createTime = DateTime.now,
      folderId = None,
      isHidden = true
    )

    fileInfoService.upload(fileInfo, readAllBytes(filePart.ref.path)).map {
      case Right(fileId) => Some(fileId)
      case _             => None
    }
  }

  def saveFile(
      key: String
  )(implicit request: Request[MultipartFormData[TemporaryFile]]): Future[Option[BSONObjectID]] = {
    request.body.file(key) match {
      case None           => Future.successful(None)
      case Some(filePart) => saveFile(filePart)
    }
  }

}

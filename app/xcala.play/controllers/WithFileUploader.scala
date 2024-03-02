package xcala.play.controllers

import xcala.play.models.FileInfo
import xcala.play.models.PreResizedImageHolder
import xcala.play.services.FileInfoService
import xcala.play.services.s3.FileStorageService
import xcala.play.utils.{TikaMimeDetector, WithExecutionContext}
import xcala.play.utils.ImagePreResizingUtils

import play.api.data.{Form, FormError}
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc._

import java.io.ByteArrayInputStream
import java.nio.file.Files.readAllBytes
import scala.concurrent.Future
import scala.util.{Failure, Success}

import com.sksamuel.scrimage.ImmutableImage
import io.sentry.Sentry
import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

object WithFileUploader {
  val AutoUploadSuffix: String = "_autoupload"
}

trait WithFileUploader extends WithExecutionContext {
  import WithFileUploader._

  private type KeyValuesPair = (String, Seq[String])

  protected val fileInfoService: FileInfoService

  private def isAutoUpload(f: MultipartFormData.FilePart[TemporaryFile]): Boolean =
    f.key.endsWith("." + AutoUploadSuffix)

  private def filePartFormatChecks(
      tempFile: MultipartFormData.FilePart[TemporaryFile]
  )(implicit message: Messages): Either[String, Seq[KeyValuesPair]] = {
    val contentAwareMimetype =
      TikaMimeDetector.guessMimeBasedOnFileContentAndName(tempFile.ref.path.toFile, tempFile.filename)
    if (
      tempFile.contentType.contains(contentAwareMimetype) &&
      (tempFile.contentType.contains("application/pdf") ||
      tempFile.contentType.exists(_.startsWith("image/")) ||
      tempFile.contentType.contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
    ) {
      Right(Seq.empty)
    } else {
      Left(Messages("error.invalidFileFormat"))
    }
  }

  private def filePartFileLengthChecks(
      f        : MultipartFormData.FilePart[TemporaryFile],
      maxLength: Option[Long]
  )(implicit message: Messages): Either[String, Seq[KeyValuesPair]] = {
    if (!maxLength.exists(_ < f.ref.path.toFile.length)) {
      Right(Seq.empty)
    } else {
      Left(Messages("error.fileToLargeMB", maxLength.get / 1024.0 / 1024.0))
    }
  }

  private def fileRatioCheck(
      f            : MultipartFormData.FilePart[TemporaryFile],
      expectedRatio: Double
  )(implicit message: Messages): Either[String, Seq[KeyValuesPair]] = {
    val image      = ImmutableImage.loader().fromFile(f.ref.path.toFile)
    val imageRatio = image.ratio
    if (imageRatio == expectedRatio) {
      Right(Seq((f.key.dropRight(AutoUploadSuffix.length + 1) + "AspectRatio") -> Seq(imageRatio.toString)))
    } else {
      Left(Messages("error.invalidImageRatio", imageRatio, expectedRatio))
    }
  }

  private def fileResolutionCheck(
      f                 : MultipartFormData.FilePart[TemporaryFile],
      expectedResolution: (Int, Int)
  )(implicit message: Messages): Either[String, Seq[KeyValuesPair]] = {
    val image = ImmutableImage.loader().fromFile(f.ref.path.toFile)
    val imageResolution: (Int, Int) = image.width -> image.height
    def resolutionRenderer(resolution: (Int, Int)) = s"${resolution._1}x${resolution._2}"
    if (imageResolution == expectedResolution) {
      Right(Nil)
    } else {
      Left(
        Messages(
          "error.invalidImageResolution",
          resolutionRenderer(imageResolution),
          resolutionRenderer(expectedResolution)
        )
      )
    }
  }

  def bindWithFilesWithCallbacks[A, B](
      form              : Form[A],
      maybeOldModel     : Option[A],
      maxLength         : Option[Long]       = None,
      maybeRatio        : Option[Double]     = None,
      maybeResolution   : Option[(Int, Int)] = None
  )(
      validationFunction: (Form[A], Form[A] => Form[A]) => Future[Either[B, B]]
  )(implicit
      messages          : Messages,
      request           : Request[MultipartFormData[TemporaryFile]]
  ): Future[B] = {
    val handlePreResizes: Boolean =
      form.value match {
        case None        => false
        case Some(value) =>
          value match {
            case _: PreResizedImageHolder =>
              true
            case _ =>
              false
          }
      }

    val fileChecks: Seq[(MultipartFormData.FilePart[TemporaryFile], Seq[FormError], Seq[KeyValuesPair])] =
      request.body.files
        .filter { file =>
          isAutoUpload(file)
        }
        .map { file: MultipartFormData.FilePart[TemporaryFile] =>
          val checkResults: Seq[Either[String, Seq[KeyValuesPair]]] =
            Seq(filePartFormatChecks(file), filePartFileLengthChecks(file, maxLength)) ++
              maybeRatio
                .map(ratio => fileRatioCheck(file, ratio)) ++
              maybeResolution
                .map(resolution => fileResolutionCheck(file, resolution))

          val (formErrors: Seq[FormError], keyValues: Seq[KeyValuesPair]) =
            checkResults.foldLeft((Seq.empty[FormError], Seq.empty[KeyValuesPair])) {
              case ((prevFormErrors, prevKeyValues), checkResult) =>
                checkResult match {
                  case Left(errorMessage) =>
                    (
                      prevFormErrors :+
                        FormError(
                          file.key.dropRight(AutoUploadSuffix.length + 1),
                          errorMessage
                        ),
                      prevKeyValues
                    )
                  case Right(keyValues)   =>
                    (prevFormErrors, prevKeyValues ++ keyValues)
                }
            }
          (file, formErrors, keyValues)
        }

    val (
      validFiles         : Seq[MultipartFormData.FilePart[TemporaryFile]],
      formErrors         : Seq[FormError],
      additionalKeyValues: Seq[KeyValuesPair]
    ) =
      fileChecks.foldLeft(
        (
          Seq.empty[MultipartFormData.FilePart[TemporaryFile]],
          Seq.empty[FormError],
          Seq.empty[KeyValuesPair]
        )
      ) {
        case (
              (
                prevValidFiles             : Seq[MultipartFormData.FilePart[TemporaryFile]],
                prevFormErrors             : Seq[FormError],
                prevAdditionalKeyValuePairs: Seq[KeyValuesPair]
              ),
              (
                file                       : MultipartFormData.FilePart[TemporaryFile],
                formErrors                 : Seq[FormError],
                keyValues                  : Seq[KeyValuesPair]
              )
            ) =>
          val nextValidFiles: Seq[MultipartFormData.FilePart[TemporaryFile]] =
            if (formErrors.isEmpty) prevValidFiles :+ file else prevValidFiles

          val nextFormErrors: Seq[FormError] =
            prevFormErrors ++ formErrors

          val nextAdditionalKeyValues: Seq[KeyValuesPair] =
            prevAdditionalKeyValuePairs ++ keyValues

          (
            nextValidFiles,
            nextFormErrors,
            nextAdditionalKeyValues
          )
      }

    val fileKeyValueFutures: Seq[Future[KeyValuesPair]] = validFiles
      .map { filePart =>
        val fieldName = filePart.key.dropRight(AutoUploadSuffix.length + 1)

        saveFile(
          filePart         = filePart,
          maybeOldModel    = maybeOldModel,
          handlePreResizes = handlePreResizes
        ).map {
          case Some(fileId) => fieldName -> Seq(fileId.stringify)
          case None         => ""        -> Seq("")
        }
      }

    Future.sequence(fileKeyValueFutures).map { fileKeyValues: Seq[KeyValuesPair] =>
      val flattenedKeyValues = (fileKeyValues ++ additionalKeyValues).groupBy(_._1).view.mapValues(x =>
        x.flatMap(_._2)
      ).flatMap {
        case (key, values) =>
          if (values.size != 1 || key.endsWith("[]")) {
            values.zipWithIndex.map { case (value, index) =>
              s"${key.replace("[]", "")}[$index]" -> value
            }
          } else {
            Seq(key -> values.head)
          }
      }
      val newData            = form.data ++ flattenedKeyValues

      {
        formErrors match {
          case Nil => form.discardingErrors.bind(newData)
          case _   => form.discardingErrors.bind(newData).withError(formErrors.head)
        }
      } -> fileKeyValues.flatMap(_._2)
    }
      .flatMap { case (finalForm, uploadedFileIds) =>
        val formWithErrorCleaner: Form[A] => Form[A] = { form =>
          form.bind(
            form.data.filter { case (_, value) =>
              !uploadedFileIds.contains(value)
            }
          )
        }
        validationFunction(finalForm, formWithErrorCleaner)
          .transformWith {
            case Success(either) =>
              either match {
                case Right(value) =>
                  Future.successful(value)

                case Left(value) =>
                  for {
                    _ <-
                      if (handlePreResizes) handleObsoletePreResizes(finalForm.value)
                      else Future.successful(())
                    _ <- handleObsoleteUploadedFiles(uploadedFileIds)

                  } yield value

              }
            case Failure(e)      =>
              {
                for {
                  _ <-
                    if (handlePreResizes) handleObsoletePreResizes(finalForm.value)
                    else Future.successful(())
                  _ <- handleObsoleteUploadedFiles(uploadedFileIds)

                } yield ()
              }.flatMap(_ => Future.failed(e))

          }
      }
  }

  def bindWithFiles[A](
      form           : Form[A],
      maxLength      : Option[Long]       = None,
      maybeRatio     : Option[Double]     = None,
      maybeResolution: Option[(Int, Int)] = None
  )(implicit
      messages       : Messages,
      request        : Request[MultipartFormData[TemporaryFile]]
  ): Future[Form[A]] = {

    val fileChecks: Seq[(MultipartFormData.FilePart[TemporaryFile], Seq[FormError], Seq[KeyValuesPair])] =
      request.body.files
        .filter { file =>
          isAutoUpload(file)
        }
        .map { file: MultipartFormData.FilePart[TemporaryFile] =>
          val checkResults: Seq[Either[String, Seq[KeyValuesPair]]] =
            Seq(filePartFormatChecks(file), filePartFileLengthChecks(file, maxLength)) ++
              maybeRatio
                .map(ratio => fileRatioCheck(file, ratio)) ++
              maybeResolution
                .map(resolution => fileResolutionCheck(file, resolution))

          val (formErrors: Seq[FormError], keyValues: Seq[KeyValuesPair]) =
            checkResults.foldLeft((Seq.empty[FormError], Seq.empty[KeyValuesPair])) {
              case ((prevFormErrors, prevKeyValues), checkResult) =>
                checkResult match {
                  case Left(errorMessage) =>
                    (
                      prevFormErrors :+
                        FormError(
                          file.key.dropRight(AutoUploadSuffix.length + 1),
                          errorMessage
                        ),
                      prevKeyValues
                    )
                  case Right(keyValues)   =>
                    (prevFormErrors, prevKeyValues ++ keyValues)
                }
            }
          (file, formErrors, keyValues)
        }

    val (
      validFiles         : Seq[MultipartFormData.FilePart[TemporaryFile]],
      formErrors         : Seq[FormError],
      additionalKeyValues: Seq[KeyValuesPair]
    ) =
      fileChecks.foldLeft(
        (
          Seq.empty[MultipartFormData.FilePart[TemporaryFile]],
          Seq.empty[FormError],
          Seq.empty[KeyValuesPair]
        )
      ) {
        case (
              (
                prevValidFiles             : Seq[MultipartFormData.FilePart[TemporaryFile]],
                prevFormErrors             : Seq[FormError],
                prevAdditionalKeyValuePairs: Seq[KeyValuesPair]
              ),
              (
                file                       : MultipartFormData.FilePart[TemporaryFile],
                formErrors                 : Seq[FormError],
                keyValues                  : Seq[KeyValuesPair]
              )
            ) =>
          val nextValidFiles: Seq[MultipartFormData.FilePart[TemporaryFile]] =
            if (formErrors.isEmpty) prevValidFiles :+ file else prevValidFiles

          val nextFormErrors: Seq[FormError] =
            prevFormErrors ++ formErrors

          val nextAdditionalKeyValues: Seq[KeyValuesPair] =
            prevAdditionalKeyValuePairs ++ keyValues

          (
            nextValidFiles,
            nextFormErrors,
            nextAdditionalKeyValues
          )
      }

    val fileKeyValueFutures: Seq[Future[KeyValuesPair]] = validFiles
      .map { filePart =>
        val fieldName = filePart.key.dropRight(AutoUploadSuffix.length + 1)

        val removeOldFileFuture = form.data.get(fieldName) match {
          case Some(oldValue) =>
            BSONObjectID.parse(oldValue) match {
              case Failure(exception) =>
                Sentry.captureException(exception)
                Future.failed(exception)
              case Success(value)     =>
                fileInfoService.removeFile(value).transformWith {
                  case Failure(exception)   =>
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
          case None           =>
            Future.successful(None)
        }

        removeOldFileFuture.flatMap { _ =>
          saveFile(
            filePart         = filePart,
            maybeOldModel    = None,
            handlePreResizes = false
          ).map {
            case Some(fileId) => fieldName -> Seq(fileId.stringify)
            case None         => ""        -> Seq("")
          }
        }
      }

    Future.sequence(fileKeyValueFutures).map { fileKeyValues: Seq[KeyValuesPair] =>
      val flattenedKeyValues = (fileKeyValues ++ additionalKeyValues).groupBy(_._1).view.mapValues(x =>
        x.flatMap(_._2)
      ).flatMap {
        case (key, values) =>
          if (values.size != 1 || key.endsWith("[]")) {
            values.zipWithIndex.map { case (value, index) =>
              s"${key.replace("[]", "")}[$index]" -> value
            }
          } else {
            Seq(key -> values.head)
          }
      }
      val newData            = form.data ++ flattenedKeyValues

      formErrors match {
        case Nil => form.discardingErrors.bind(newData)
        case _   => form.discardingErrors.bind(newData).withError(formErrors.head)
      }
    }
  }

  private def handleNewPreResizes[A <: PreResizedImageHolder](
      maybeOldModel : Option[A],
      newFileId     : BSONObjectID,
      newFileContent: Array[Byte],
      newFileName   : String
  ): Future[Unit] = {
    implicit val fileStorageService: FileStorageService = fileInfoService.fileStorageService
    if (!maybeOldModel.map(_.maybeImageFileId).contains(Some(newFileId))) {
      for {
        _ <- maybeOldModel.map { oldModel =>
          // removing old model resize images
          ImagePreResizingUtils.removePreResizes(oldModel)

        }.getOrElse {
          Future.successful(())
        }

        _ <- ImagePreResizingUtils.uploadPreResizesRaw(
          imageFileId      = newFileId,
          fileContent      = new ByteArrayInputStream(newFileContent),
          fileOriginalName = newFileName
        )

      } yield ()

    } else {
      Future.successful(())
    }
  }

  private def handleObsoletePreResizes[A](
      maybeNewModel: Option[A]
  ): Future[Unit] =
    maybeNewModel match {
      case None        =>
        Future.successful(())
      case Some(value) =>
        value match {
          case preResizedImageHolder: PreResizedImageHolder =>
            ImagePreResizingUtils.removePreResizes(preResizedImageHolder)(
              fileStorageService = fileInfoService.fileStorageService,
              ec                 = ec
            ).map(_ => ())
          case _ =>
            Future.successful(())
        }
    }

  private def handleObsoleteUploadedFiles(
      filesIds: Seq[String]
  ): Future[_] =
    Future.traverse(filesIds.flatMap(BSONObjectID.parse(_).toOption))(fileInfoService.removeFile)

  private def saveFile[A](
      filePart        : MultipartFormData.FilePart[TemporaryFile],
      maybeOldModel   : Option[A],
      handlePreResizes: Boolean
  ): Future[Option[BSONObjectID]] = {
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
        {
          if (handlePreResizes) {

            handleNewPreResizes(
              maybeOldModel  = maybeOldModel.asInstanceOf[Option[PreResizedImageHolder]],
              newFileId      = fileId,
              newFileContent = fileContentByteArray,
              newFileName    = fileInfo.name
            )

          } else {
            Future.successful(())
          }
        }.map { _ =>
          Some(fileId)
        }
      case _             =>
        Future.successful(None)
    }
  }

}

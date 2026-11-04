package xcala.play.cross.controllers

import xcala.play.controllers.WithFileUploader.AutoUploadSuffix
import xcala.play.cross.models._
import xcala.play.cross.services._
import xcala.play.cross.services.ImageTranscodingService
import xcala.play.cross.services.s3.FileStorageService
import xcala.play.cross.utils.LazyInjector
import xcala.play.utils.TikaMimeDetector
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.data.FormError
import play.api.i18n.Messages
import play.api.libs.Files.TemporaryFile
import play.api.mvc.MultipartFormData
import play.api.mvc.Request
import play.api.mvc.RequestHeader

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.sksamuel.scrimage.ImmutableImage
import reactivemongo.api.bson.BSONObjectID

trait CrossWithFileUploader[Id] extends WithExecutionContext {
  protected val fileInfoService: CrossFileInfoService[Id, _]

  protected type KeyValuesPair = (String, Seq[String])

  lazy val imageTranscodingService: ImageTranscodingService =
    LazyInjector.injector.instanceOf[ImageTranscodingProviderService].transcodingService

  protected def isAutoUpload(f: MultipartFormData.FilePart[TemporaryFile]): Boolean =
    f.key.endsWith("." + AutoUploadSuffix)

  protected def filePartFormatChecks(
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

  protected def filePartFileLengthChecks(
      f        : MultipartFormData.FilePart[TemporaryFile],
      maxLength: Option[Long]
  )(implicit message: Messages): Either[String, Seq[KeyValuesPair]] = {
    if (!maxLength.exists(_ < f.ref.path.toFile.length)) {
      Right(Seq.empty)
    } else {
      Left(Messages("error.fileToLargeMB", maxLength.get / 1024.0 / 1024.0))
    }
  }

  protected def fileRatioCheck(
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

  protected def fileResolutionCheck(
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
      form           : Form[A],
      maybeOldModel  : Option[A],
      maxLength      : Option[Long]       = None,
      maybeRatio     : Option[Double]     = None,
      maybeResolution: Option[(Int, Int)] = None
  )(
      validationFunction: (Form[A], Form[A] => Form[A]) => Future[Either[B, B]]
  )(implicit
      messages: Messages,
      request : Request[MultipartFormData[TemporaryFile]]
  ): Future[B] = {
    val handlePreResizes: Boolean =
      form.value match {
        case None => false
        case Some(value) =>
          value match {
            case _: PreResizedImageHolder[_] =>
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
                  case Right(keyValues) =>
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
                file      : MultipartFormData.FilePart[TemporaryFile],
                formErrors: Seq[FormError],
                keyValues : Seq[KeyValuesPair]
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
        ).flatMap {
          case Left(error) =>
            Future.failed(new Throwable(error))
          case Right(value) =>
            Future.successful {
              value match {
                case Some(fileId) =>
                  fieldName ->
                    Seq(
                      fileId match {
                        case x: BSONObjectID => x.stringify
                        case x => x.toString
                      }
                    )
                case None =>
                  "" -> Seq("")
              }
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
      val newData = form.data ++ flattenedKeyValues

      {
        formErrors match {
          case Nil => form.discardingErrors.bind(newData)
          case _   => form.discardingErrors.bind(newData).withError(formErrors.head)
        }
      } -> fileKeyValues.flatMap(_._2)
    }
      .flatMap { case (finalForm, uploadedFileIds) =>
        val formWithErrorCleaner: Form[A] => Form[A] = { form =>
          form.copy(
            data = form.data.filter { case (_, value) =>
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
            case Failure(e) =>
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

  protected def handleNewPreResizes[A <: PreResizedImageHolder[Id]](
      maybeOldModel: Option[A],
      newFileId    : Id
  )(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Unit]] = {
    implicit val fileStorageService: FileStorageService = fileInfoService.fileStorageService
    if (!maybeOldModel.map(_.maybeImageFileId).contains[Option[Id]](Some(newFileId))) {
      for {
        _ <- maybeOldModel.map { oldModel =>
          // removing old model resize images
          imageTranscodingService.removePreResizes(oldModel)

        }.getOrElse {
          Future.successful(())
        }

        resizingResult <- imageTranscodingService.uploadPreResizesByFileId(
          fileId = newFileId
        )

      } yield resizingResult

    } else {
      Future.successful(Right(()))
    }
  }

  protected def handleObsoletePreResizes[A](
      maybeNewModel: Option[A]
  )(implicit
      requestHeader: RequestHeader
  ): Future[Unit] =
    maybeNewModel match {
      case None =>
        Future.successful(())
      case Some(value) =>
        value match {
          case preResizedImageHolder: PreResizedImageHolder[_] =>
            imageTranscodingService.removePreResizes(preResizedImageHolder)(
              fileStorageService = fileInfoService.fileStorageService,
              ec                 = ec,
              requestHeader      = requestHeader
            ).map(_ => ())
          case _ =>
            Future.successful(())
        }
    }

  protected def handleObsoleteUploadedFiles(
      filesIds: Seq[String]
  )(implicit requestHeader: RequestHeader): Future[Seq[Either[String, Int]]]

  protected def saveFile[A](
      filePart        : MultipartFormData.FilePart[TemporaryFile],
      maybeOldModel   : Option[A],
      handlePreResizes: Boolean
  )(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Option[Id]]]

}

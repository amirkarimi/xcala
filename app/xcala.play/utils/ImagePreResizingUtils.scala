package xcala.play.utils

import xcala.play.models.ImageRenders
import xcala.play.models.PreResizedImageHolder
import xcala.play.services.s3.FileStorageService

import java.io.ByteArrayOutputStream
import java.io.InputStream
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Using

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import com.sksamuel.scrimage.metadata.ImageMetadata
import com.sksamuel.scrimage.webp.WebpWriter
import io.sentry.Sentry
import reactivemongo.api.bson.BSONObjectID

object ImagePreResizingUtils {

  def removePreResizes(
      preResizedImageHolder: PreResizedImageHolder
  )(
      implicit
      fileStorageService   : FileStorageService,
      ec                   : ExecutionContext
  ): Future[_] = {
    preResizedImageHolder.maybeImageFileId match {
      case Some(imageFileId) =>
        Future.traverse(ImageRenders.ImageResizedRenderType.all) { allowedResize =>
          val resizedFileName: String = allowedResize.resizedObjectName(imageFileId.stringify)
          fileStorageService.deleteByObjectName(resizedFileName).transformWith {
            case Failure(_) =>
              Future.successful(())

            case Success(_) =>
              Future.successful(())
          }
        }
      case _                 =>
        Future.successful(())
    }

  }

  def uploadPreResizesRaw(
      imageFileId       : BSONObjectID,
      fileContent       : InputStream,
      fileOriginalName  : String
  )(
      implicit
      fileStorageService: FileStorageService,
      ec                : ExecutionContext
  ): Future[_] =
    Future.fromTry(
      Using(fileContent) { fileContent =>
        val originalImage: ImmutableImage = ImmutableImage
          .loader()
          .fromStream(fileContent)

        Future.traverse(ImageRenders.ImageResizedRenderType.all) { allowedResize =>
          val resizedFileName: String = allowedResize.resizedObjectName(imageFileId.stringify)

          val bos = new ByteArrayOutputStream()

          val resizedImage =
            originalImage
              .scaleToWidth(
                allowedResize.overriddenWidth.toInt,
                ScaleMethod.Lanczos3
              )
          WebpWriter.MAX_LOSSLESS_COMPRESSION.write(
            resizedImage,
            ImageMetadata.fromImage(resizedImage),
            bos
          )

          bos.close()

          val byteArray = bos.toByteArray

          fileStorageService.upload(
            objectName   = resizedFileName,
            content      = byteArray,
            contentType  = "image/webp",
            originalName = fileOriginalName
          )

        }
      }
    ).flatten

  def uploadPreResizes(
      preResizedImageHolder: PreResizedImageHolder
  )(
      implicit
      fileStorageService   : FileStorageService,
      ec                   : ExecutionContext
  ): Future[Unit] = {
    preResizedImageHolder.maybeImageFileId match {
      case Some(imageFileId) =>
        fileStorageService.findByObjectName(imageFileId.stringify).map {
          file =>
            uploadPreResizesRaw(
              imageFileId      = imageFileId,
              fileContent      = file.content,
              fileOriginalName = file.originalName
            ).transformWith {
              case Failure(exception) =>
                Sentry.captureException(exception)
                Future.failed(exception)

              case Success(_) =>
                Future.successful(())

            }

        }
      case _                 => Future.successful(())
    }

  }

}

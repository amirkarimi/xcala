package xcala.play.cross.controllers

import xcala.play.utils.WithExecutionContext

import akka.http.scaladsl.model.MediaTypes
import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.http.HttpEntity
import play.api.mvc.ResponseHeader
import play.api.mvc.Result

import java.io.ByteArrayOutputStream
import scala.concurrent._
import scala.concurrent.Future

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import com.sksamuel.scrimage.metadata.ImageMetadata
import com.sksamuel.scrimage.webp.WebpWriter

trait ImageRendererController extends WithExecutionContext {

  protected def renderImage(
      image             : ImmutableImage,
      width             : Option[Int],
      height            : Option[Int],
      contentType       : String,
      widthToHeightRatio: Double
  ): Future[Result] = {
    val stream =
      Source.futureSource {
        Future {
          blocking {
            val outImage = (width, height) match {
              case (Some(width), Some(height)) =>
                if ((width.toDouble / height) > widthToHeightRatio) {
                  image.scaleToHeight(height, ScaleMethod.Progressive)
                } else if ((width.toDouble / height) < widthToHeightRatio) {
                  image.scaleToWidth(width, ScaleMethod.Progressive)
                } else {
                  image.cover(width, height)
                }
              case (Some(width), None) =>
                image.scaleToWidth(width)
              case (None, Some(height)) =>
                image.scaleToHeight(height)
              case _ =>
                throw new IllegalArgumentException()
            }

            val bos = new ByteArrayOutputStream()
            getImageWriter(contentType).write(outImage, ImageMetadata.fromImage(outImage), bos)
            Source.single(ByteString(bos.toByteArray))
          }
        }
      }

    val body = HttpEntity.Streamed(
      stream,
      None,
      Some(MediaTypes.`image/webp`.value)
    )

    Future.successful(
      Result(
        header = ResponseHeader(status = 200),
        body   = body
      )
    )

  }

  protected def getImageWriter(contentType: String): WebpWriter = contentType match {
    // case "image/gif" => Gif2WebpWriter.DEFAULT
    case _ => WebpWriter.MAX_LOSSLESS_COMPRESSION.withMultiThread
  }

}

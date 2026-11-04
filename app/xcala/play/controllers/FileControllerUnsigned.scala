package xcala.play.controllers

import play.api.mvc._

import java.net.SocketTimeoutException
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Using

import com.sksamuel.scrimage.ImmutableImage
import io.sentry.Hint
import io.sentry.Sentry
import reactivemongo.api.bson.BSONObjectID

trait FileControllerUnsigned extends FileControllerBase {

  def getImage(
      id    : String,
      width : Option[Int],
      height: Option[Int]
  ): Action[AnyContent] = Action.async { implicit request =>
    BSONObjectID.parse(id.split('.').headOption.getOrElse(id)) match {
      case Success(bsonObjectId) =>
        fileInfoService.findObjectById(bsonObjectId).transformWith {
          case Success(file) if !file.isImage =>
            Future.successful(renderFile(file, CONTENT_DISPOSITION_INLINE))
          case Success(file) if file.isImage && width.isEmpty && height.isEmpty =>
            Future.successful(renderFile(file, CONTENT_DISPOSITION_INLINE))
          case Success(file) if file.isImage =>
            var closedInputStream = false
            cache
              .getOrElseUpdate(s"image$id${width.getOrElse("")}${height.getOrElse("")}") {
                Future {
                  Using(file.content) { stream =>
                    val image: ImmutableImage = ImmutableImage.loader().fromStream(stream)

                    val safeWidth =
                      Seq(configuration.getOptional[Int]("file.image.maxResize.width"), width).flatten
                        .reduceOption(_ min _)
                    val safeHeight =
                      Seq(configuration.getOptional[Int]("file.image.maxResize.height"), height).flatten
                        .reduceOption(_ min _)

                    val widthToHeightRatio: Double = image.width.toDouble / image.height

                    renderImage(
                      image              = image,
                      width              = safeWidth,
                      height             = safeHeight,
                      contentType        = file.contentType.getOrElse(""),
                      widthToHeightRatio = widthToHeightRatio
                    )
                  }
                }.transformWith { x =>
                  closedInputStream = true
                  x.flatten match {
                    case Success(x) =>
                      Future.successful(x)
                    case Failure(e) =>
                      Future.failed(e)
                  }
                }.flatten
              }
              .transformWith {
                case Success(result) =>
                  if (!closedInputStream) {
                    file.content.close()
                  }
                  Future.successful(result)
                case Failure(e) =>
                  if (!closedInputStream) {
                    file.content.close()
                  }
                  val hint = new Hint
                  hint.set("hint", "An Error has occurred while loading the image file: " + id)
                  Sentry.captureException(e, hint)
                  Future.successful(InternalServerError)
              }
          case Failure(e) =>
            e match {
              case _: SocketTimeoutException =>
                Future.successful(InternalServerError)

              case e if e.getMessage.toLowerCase.contains("not exist") =>
                Future.successful(NotFound)

              case e =>
                Sentry.captureException(e)
                Future.successful(InternalServerError)
            }

          case _ => ???

        }
      case Failure(exception) =>
        val hint = new Hint
        hint.set("id", id)
        Sentry.captureException(exception)
        Future.successful(NotFound)
    }

  }

  def getFile(
      id                      : BSONObjectID,
      maybeUserDefinedFileName: Option[String]
  ): Action[AnyContent] =
    Action.async { implicit request =>
      renderFile(
        id                       = id,
        dispositionMode          = CONTENT_DISPOSITION_ATTACHMENT,
        maybeUserDefinedFileName = maybeUserDefinedFileName
      )
    }

}

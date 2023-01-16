package xcala.play.controllers

import xcala.play.models._
import xcala.play.utils.SourceUtils

import play.api.mvc._

import java.net.SocketTimeoutException
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import com.sksamuel.scrimage.ImmutableImage
import io.sentry.Hint
import io.sentry.Sentry
import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

trait FileControllerSigned extends FileControllerBase {

  def imageProtectionCheck(
      expectedToBeProtected: Boolean,
      signature: String,
      expireTime: Option[DateTime]
  )(
      unverifiedId: BSONObjectID
  )(
      protectedContent: BSONObjectID => Action[AnyContent]
  ): Action[AnyContent] = {
    val imageSignatureParameters: ImageSignatureParameters =
      if (expectedToBeProtected) {
        ProtectedImageSignatureParameters(unverifiedId, expireTime.get)
      } else {
        PublicImageSignatureParameters(unverifiedId)
      }

    if (imageSignatureParameters.isValid(signature)) {
      protectedContent(unverifiedId)
    } else {
      Action.async {
        Future.successful(Forbidden(views.html.xcala.play.expired()))
      }
    }
  }

  def fileProtectionCheck(
      expectedToBeProtected: Boolean,
      signature: String,
      expireTime: Option[DateTime]
  )(
      unverifiedId: BSONObjectID
  )(
      protectedContent: BSONObjectID => Action[AnyContent]
  ): Action[AnyContent] = {
    val fileSignatureParameters: FileSignatureParameters =
      if (expectedToBeProtected) {
        ProtectedFileSignatureParameters(unverifiedId, expireTime.get)
      } else {
        PublicFileSignatureParameters(unverifiedId)
      }

    if (fileSignatureParameters.isValid(signature)) {
      protectedContent(unverifiedId)
    } else {
      Action.async {
        Future.successful(Forbidden(views.html.xcala.play.expired()))
      }
    }
  }

  private def getImage(
      unverifiedId: BSONObjectID,
      signature: String,
      width: Option[Int],
      height: Option[Int],
      protectedAccess: Boolean,
      expireTime: Option[DateTime]
  ): Action[AnyContent] =
    imageProtectionCheck(protectedAccess, signature, expireTime)(unverifiedId) { verifiedId =>
      (if (protectedAccess) action else Action).async {
        fileInfoService.findObjectById(verifiedId).transformWith {
          case Success(file) if !file.isImage =>
            Future.successful(renderFile(file, CONTENT_DISPOSITION_INLINE))
          case Success(file) if file.isImage && width.isEmpty && height.isEmpty =>
            Future.successful(renderFile(file, CONTENT_DISPOSITION_INLINE))
          case Success(file) if file.isImage =>
            var closedInputStream = false

            cache
              .getOrElseUpdate(s"image$verifiedId${width.getOrElse("")}${height.getOrElse("")}") {
                Future {
                  SourceUtils
                    .using(file.content) { stream =>
                      val image: ImmutableImage = ImmutableImage.loader().fromStream(stream)

                      val safeWidth =
                        Seq(configuration.getOptional[Int]("file.image.maxResize.width"), width).flatten
                          .reduceOption(_ min _)
                      val safeHeight =
                        Seq(configuration.getOptional[Int]("file.image.maxResize.height"), height).flatten
                          .reduceOption(_ min _)

                      val widthToHeightRatio: Double = image.width.toDouble / image.height

                      renderImage(
                        image,
                        safeWidth,
                        safeHeight,
                        file.contentType.getOrElse(""),
                        widthToHeightRatio
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
                  hint.set("hint", "An Error has occurred while loading the image file: " + verifiedId)
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
      }
    }

  private def getFile(
      unverifiedId: BSONObjectID,
      signature: String,
      protectedAccess: Boolean,
      expireTime: Option[DateTime]
  ): Action[AnyContent] =
    fileProtectionCheck(protectedAccess, signature, expireTime)(unverifiedId) { verifiedId =>
      (if (protectedAccess) action else Action).async {
        renderFile(verifiedId, CONTENT_DISPOSITION_ATTACHMENT)
      }
    }

  def getProtectedImage(
      id: BSONObjectID,
      signature: String,
      width: Option[Int],
      height: Option[Int],
      expireTime: Long
  ): Action[AnyContent] =
    getImage(id, signature, width, height, protectedAccess = true, Some(new DateTime(expireTime)))

  def getProtectedFile(
      id: BSONObjectID,
      signature: String,
      expireTime: Long
  ): Action[AnyContent] =
    getFile(id, signature, protectedAccess = true, Some(new DateTime(expireTime)))

  def getPublicImage(
      id: String,
      signature: String,
      width: Option[Int],
      height: Option[Int],
      @annotation.nowarn extension: Option[String]
  ): Action[AnyContent] = {
    BSONObjectID.parse(id) match {
      case Success(preProcessedUnverifiedId) =>
        getImage(preProcessedUnverifiedId, signature, width, height, protectedAccess = false, None)

      case Failure(exception) =>
        val hint = new Hint
        hint.set("id", id)
        Sentry.captureException(exception, hint)
        Action.async {
          Future.successful(NotFound)
        }
    }
  }

  def getPublicImage(
      id: BSONObjectID,
      signature: String,
      width: Option[Int],
      height: Option[Int]
  ): Action[AnyContent] =
    getImage(id, signature, width, height, protectedAccess = false, None)

  def getPublicFile(
      id: BSONObjectID,
      signature: String
  ): Action[AnyContent] =
    getFile(id, signature, protectedAccess = false, None)

}

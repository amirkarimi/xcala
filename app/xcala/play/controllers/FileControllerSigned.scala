package xcala.play.controllers

import xcala.play.models._

import play.api.mvc._

import java.net.SocketTimeoutException
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Using

import com.sksamuel.scrimage.ImmutableImage
import io.sentry.Hint
import io.sentry.Sentry
import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

trait FileControllerSigned extends FileControllerBase {

  def imageProtectionCheck(
      expectedToBeProtected: Boolean,
      signature            : String,
      expireTime           : Option[DateTime]
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
      signature            : String,
      expireTime           : Option[DateTime]
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

  def protectedAction: ActionBuilder[Request, AnyContent]

  private def getImage(
      unverifiedId   : BSONObjectID,
      signature      : String,
      width          : Option[Int],
      height         : Option[Int],
      protectedAccess: Boolean,
      expireTime     : Option[DateTime]
  ): Action[AnyContent] =
    imageProtectionCheck(
      expectedToBeProtected = protectedAccess,
      signature             = signature,
      expireTime            = expireTime
    )(
      unverifiedId
    ) { verifiedId =>
      (if (protectedAccess) protectedAction else Action).async { implicit request =>
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
      unverifiedId            : BSONObjectID,
      signature               : String,
      protectedAccess         : Boolean,
      expireTime              : Option[DateTime],
      maybeUserDefinedFileName: Option[String]
  ): Action[AnyContent] =
    fileProtectionCheck(
      expectedToBeProtected = protectedAccess,
      signature             = signature,
      expireTime            = expireTime
    )(
      unverifiedId
    ) { verifiedId =>
      (if (protectedAccess) protectedAction else Action).async { implicit request =>
        renderFile(
          id                       = verifiedId,
          dispositionMode          = CONTENT_DISPOSITION_ATTACHMENT,
          maybeUserDefinedFileName = maybeUserDefinedFileName
        )
      }
    }

  def getProtectedImage(
      id        : BSONObjectID,
      signature : String,
      width     : Option[Int],
      height    : Option[Int],
      expireTime: Long
  ): Action[AnyContent] =
    getImage(
      unverifiedId    = id,
      signature       = signature,
      width           = width,
      height          = height,
      protectedAccess = true,
      expireTime      = Some(new DateTime(expireTime))
    )

  def getProtectedFile(
      id                      : BSONObjectID,
      signature               : String,
      expireTime              : Long,
      maybeUserDefinedFileName: Option[String]
  ): Action[AnyContent] =
    getFile(
      unverifiedId             = id,
      signature                = signature,
      protectedAccess          = true,
      expireTime               = Some(new DateTime(expireTime)),
      maybeUserDefinedFileName = maybeUserDefinedFileName
    )

  def getPublicImage(
      id                          : String,
      signature                   : String,
      width                       : Option[Int],
      height                      : Option[Int],
      @annotation.nowarn extension: Option[String]
  ): Action[AnyContent] = {
    BSONObjectID.parse(id) match {
      case Success(preProcessedUnverifiedId) =>
        getImage(
          unverifiedId    = preProcessedUnverifiedId,
          signature       = signature,
          width           = width,
          height          = height,
          protectedAccess = false,
          expireTime      = None
        )

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
      id       : BSONObjectID,
      signature: String,
      width    : Option[Int],
      height   : Option[Int]
  ): Action[AnyContent] =
    getImage(
      unverifiedId    = id,
      signature       = signature,
      width           = width,
      height          = height,
      protectedAccess = false,
      expireTime      = None
    )

  def getPublicFile(
      id                      : BSONObjectID,
      signature               : String,
      maybeUserDefinedFileName: Option[String]
  ): Action[AnyContent] =
    getFile(
      unverifiedId             = id,
      signature                = signature,
      protectedAccess          = false,
      expireTime               = None,
      maybeUserDefinedFileName = maybeUserDefinedFileName
    )

}

package xcala.play.controllers

import xcala.play.models._
import xcala.play.services._
import xcala.play.utils.BaseStorageUrls
import xcala.play.utils.WithExecutionContext

import akka.actor.ActorSystem
import akka.http.scaladsl.model.MediaTypes
import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.http.HttpEntity
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.InjectedController
import play.api.mvc.MultipartFormData
import play.api.mvc.Result

import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.nio.file.Files.readAllBytes
import scala.concurrent._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import com.sksamuel.scrimage.metadata.ImageMetadata
import com.sksamuel.scrimage.webp.WebpWriter
import io.sentry.Hint
import io.sentry.Sentry
import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import reactivemongo.api.bson._

private[controllers] trait FileControllerBase
    extends InjectedController
    with WithComposableActions
    with WithExecutionContext
    with I18nSupport {

  implicit val messagesProvider: Messages
  val fileInfoService: FileInfoService
  val folderService: FolderService
  val publicStorageUrls: BaseStorageUrls.PublicStorageUrls
  val cache: AsyncCacheApi
  val actorSystem: ActorSystem
  implicit val configuration: Configuration
  implicit val mat: Materializer

  def defaultInternalServerError(implicit adminRequest: RequestType[_]): Result

  protected val CONTENT_DISPOSITION_ATTACHMENT = "attachment"
  protected val CONTENT_DISPOSITION_INLINE     = "inline"

  def selector: Action[AnyContent]

  def browser(ckEditorFuncNum: Int, fileType: Option[String]): Action[AnyContent]

  protected def getListView(
      files: Seq[FileInfo],
      folders: Seq[Folder],
      realFolderAndParents: List[Folder]
  )(implicit request: RequestType[_]): Result

  private def getList(
      folderId: Option[BSONObjectID],
      fileType: Option[String]
  )(implicit request: RequestType[_]): Future[Result] = {
    for {
      files            <- fileInfoService.getFilesUnderFolder(folderId, fileType)
      folders          <- folderService.getFoldersUnderFolder(folderId)
      folderAndParents <- folderService.getFolderAndParents(folderId)

      realFolderAndParents = Folder(None, Messages("root"), None) :: folderAndParents
    } yield {
      getListView(files, folders, realFolderAndParents)
    }
  }

  def getList(
      folderId: Option[BSONObjectID],
      fileId: Option[BSONObjectID],
      fileType: Option[String]
  ): Action[AnyContent] = action.async { implicit request =>
    val finalFolderId = fileId match {
      case None         => Future.successful(folderId)
      case Some(fileId) => fileInfoService.findById(fileId).map(_.flatMap(_.folderId))
    }

    finalFolderId.flatMap(getList(_, fileType))
  }

  def uploadResult(folderId: Option[BSONObjectID], body: MultipartFormData[Files.TemporaryFile])(implicit
      requestHeader: RequestHeader
  ): Future[Result] = {
    val resultsFuture: Future[Seq[String]] = Future.sequence(
      body.files.sortBy(_.filename).map { file =>
        val fileExtension = FilenameUtils.getExtension(file.filename)
        val id            = BSONObjectID.generate()

        val fileInfo = FileInfo(
          id = Some(id),
          name = file.filename,
          extension = fileExtension,
          contentType = file.contentType.getOrElse("unknown"),
          length = file.ref.path.toFile.length,
          createTime = DateTime.now,
          folderId = folderId,
          isHidden = false
        )

        fileInfoService.upload(fileInfo, readAllBytes(file.ref.path)).flatMap {
          case Right(fileId) =>
            Future.successful(
              s"""{"id":"${fileId.stringify}", "label":"${fileInfo.name}", "url":"${if (fileInfo.isImage) {
                  publicStorageUrls.publicImageUrl(fileId).absoluteURL()
                } else {
                  publicStorageUrls.publicFileUrl(fileId).absoluteURL()
                }}"}"""
            )

          case Left(errorMessage) =>
            val exception = new Throwable(errorMessage)
            Sentry.captureException(exception)
            Future.failed(exception)
        }
      }
    )

    resultsFuture
      .map { results =>
        Ok(results.mkString("[", ",", "]"))
      }
      .recover { case _ =>
        BadRequest
      }
  }

  def upload(folderId: Option[BSONObjectID]): Action[MultipartFormData[Files.TemporaryFile]]

  def createFolder: Action[JsValue] = action.async(parse.json) { implicit request: RequestType[JsValue] =>
    val json               = request.body
    val folderNameOpt      = (json \ "folderName").asOpt[String]
    val currentFolderIDOpt = (json \ "currentFolderId").asOpt[String].flatMap(BSONObjectID.parse(_).toOption)

    folderNameOpt match {
      case Some(folderName) => folderService.insert(Folder(None, folderName, currentFolderIDOpt)).map(_ => Ok("OK"))
      case _                => Future.successful(BadRequest)
    }
  }

  def getFileInfo: Action[JsValue] = action.async(parse.json) { implicit request =>
    val input = request.body
    val idOpt = (input \ "id").asOpt[String].flatMap(BSONObjectID.parse(_).toOption)
    idOpt
      .map { id =>
        fileInfoService.findById(id).map {
          case None => Ok("{}")
          case Some(file) =>
            Ok(
              Json.obj(
                "filename"    -> file.name,
                "contentType" -> file.contentType
              )
            )
        }
      }
      .getOrElse {
        Future.successful(Ok("{}"))
      }
  }

  def rename(id: BSONObjectID, itemType: String, newName: String): Action[AnyContent] = action.async {
    val future = itemType match {
      case "folder" =>
        folderService.renameFolder(id, newName)
      case "file" =>
        fileInfoService.renameFile(id, newName)
    }

    future.map { _ =>
      Ok("Ok")
    }
  }

  def remove(id: BSONObjectID, itemType: String): Action[AnyContent] = action.async { implicit request =>
    val future = itemType match {
      case "folder" =>
        folderService.removeFolder(id)
      case "file" =>
        fileInfoService.removeFile(id).flatMap {
          case Left(errorMessage) =>
            val exception = new Throwable(errorMessage)
            Sentry.captureException(exception)
            Future.failed(exception)
          case Right(value) =>
            Future.successful(Some(value))
        }
    }

    future
      .map { _ =>
        Ok("Ok")
      }
      .recover { case e: Throwable =>
        Sentry.captureException(e)
        defaultInternalServerError
      }
  }

  protected def renderFile(file: FileInfoService.FileObject, dispositionMode: String): Result = {
    val res: Source[ByteString, Future[IOResult]] =
      StreamConverters
        .fromInputStream(() => file.content)
        .idleTimeout(15.seconds)
        .initialTimeout(15.seconds)
        .completionTimeout(60.seconds)
        .recover { case e: Throwable =>
          val hint = new Hint
          hint.set("hint", "on stream recover")
          Sentry.captureException(e, hint)
          file.content.close()
          ByteString.empty
        }

    // Just to make sure stream will be closed even if the file download is cancelled or not finished for any reason
    // Please note that this schedule will be cancelled once the stream is completed.
    val cancellable = actorSystem.scheduler.scheduleOnce(4.minutes) {
      try {
        file.content.close()
      } catch {
        case e: Throwable =>
          val hint = new Hint
          hint.set("hint", "on inactivity cleanup schedule")
          Sentry.captureException(e, hint)
      }
    }

    // Add an extra stage for doing cleanups and cancelling the scheduled task
    val lazyFlow = Flow[ByteString]
      .concatLazy(Source.lazyFuture { () =>
        Future {
          file.content.close()
          cancellable.cancel()
          ByteString.empty
        }
      })

    val withCleanupRes = res.via(lazyFlow)

    val body = HttpEntity.Streamed.apply(withCleanupRes, file.contentLength, file.contentType)

    Result(
      header = ResponseHeader(OK),
      body = body
    ).withHeaders(
      CONTENT_LENGTH -> file.contentLength.map(_.toString).getOrElse(""),
      CONTENT_DISPOSITION -> (s"""$dispositionMode; filename="${java.net.URLEncoder
          .encode(file.name, "UTF-8")
          .replace("+", "%20")}"; filename*=UTF-8''""" + java.net.URLEncoder
        .encode(file.name, "UTF-8")
        .replace("+", "%20"))
    )
  }

  protected def renderFile(id: BSONObjectID, dispositionMode: String): Future[Result] = {
    fileInfoService.findObjectById(id).transform {

      case Success(file) =>
        Success(renderFile(file, dispositionMode))

      case Failure(e) =>
        e match {
          case _: SocketTimeoutException =>
            Success(InternalServerError)

          case e if e.getMessage.toLowerCase.contains("not exist") =>
            Success(NotFound)

          case e =>
            Sentry.captureException(e)
            Success(InternalServerError)
        }
    }
  }

  protected def renderImage(
      image: ImmutableImage,
      width: Option[Int],
      height: Option[Int],
      contentType: String,
      widthToHeightRatio: Double
  ): Future[Result] =
    Future {
      blocking {
        val outImage = (width, height) match {
          case (Some(width), Some(height)) =>
            if ((width.toDouble / height) > widthToHeightRatio) {
              image.scaleToHeight(height, ScaleMethod.Lanczos3)
            } else if ((width.toDouble / height) < widthToHeightRatio) {
              image.scaleToWidth(width, ScaleMethod.Lanczos3)
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

        bos.close()

        val body = HttpEntity.Strict(
          ByteString(bos.toByteArray),
          Some(MediaTypes.`image/webp`.value)
        )
        Result(header = ResponseHeader(200), body)
      }
    }

  protected def getImageWriter(contentType: String): WebpWriter = contentType match {
    // case "image/gif" => Gif2WebpWriter.DEFAULT
    case _ => WebpWriter.DEFAULT
  }

}

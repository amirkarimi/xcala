package xcala.play.controllers

import xcala.play.cross.controllers.ImageRendererController
import xcala.play.cross.services.ImageTranscodingProviderService
import xcala.play.cross.services.ImageTranscodingService
import xcala.play.cross.utils.LazyInjector
import xcala.play.models._
import xcala.play.services._
import xcala.play.utils.BaseStorageUrls
import xcala.play.utils.WithExecutionContext

import akka.actor.ActorSystem
import akka.stream.IOResult
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import play.api.Configuration
import play.api.cache.AsyncCacheApi
import play.api.http.HttpEntity
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.libs.json.JsValue
import play.api.mvc._
import play.api.mvc.Action
import play.api.mvc.AnyContent
import play.api.mvc.InjectedController
import play.api.mvc.MultipartFormData
import play.api.mvc.Result

import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.file.Files.readAllBytes
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success

import io.sentry.Hint
import io.sentry.Sentry
import org.apache.commons.io.FilenameUtils
import org.joda.time.DateTime
import reactivemongo.api.bson._

private[controllers] trait FileControllerBase
    extends InjectedController
    with WithComposableActions
    with I18nSupport
    with WithExecutionContext
    with ImageRendererController {

  implicit val messagesProvider: Messages
  val fileInfoService          : FileInfoService
  val folderService            : FolderService
  val publicStorageUrls        : BaseStorageUrls.PublicStorageUrls
  val cache                    : AsyncCacheApi
  val actorSystem              : ActorSystem
  implicit val configuration   : Configuration
  implicit val mat             : Materializer

  lazy val imageTranscodingService: ImageTranscodingService =
    LazyInjector.injector.instanceOf[ImageTranscodingProviderService].transcodingService

  def defaultInternalServerError(implicit adminRequest: RequestType[_]): Result

  protected val CONTENT_DISPOSITION_ATTACHMENT: String = "attachment"
  protected val CONTENT_DISPOSITION_INLINE    : String = "inline"

  def selector: Action[AnyContent]

  def browser(ckEditorFuncNum: Int, fileType: Option[String]): Action[AnyContent]

  protected def getListView(
      files               : Seq[FileInfo],
      folders             : Seq[Folder],
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

      realFolderAndParents = Folder(id = None, name = Messages("root"), parent = None) :: folderAndParents
    } yield {
      getListView(files = files, folders = folders, realFolderAndParents = realFolderAndParents)
    }
  }

  def getList(
      folderId: Option[BSONObjectID],
      fileId  : Option[BSONObjectID],
      fileType: Option[String]
  ): Action[AnyContent] = action.async { implicit request =>
    val finalFolderId = fileId match {
      case None         => Future.successful(folderId)
      case Some(fileId) => fileInfoService.findById(fileId).map(_.flatMap(_.folderId))
    }

    finalFolderId.flatMap(getList(_, fileType))
  }

  def uploadResult(
      folderId        : Option[BSONObjectID],
      body            : MultipartFormData[Files.TemporaryFile],
      handlePreResizes: Boolean = false
  )(implicit
      requestHeader: RequestHeader
  ): Future[Result] = {
    val resultsFuture: Future[Seq[String]] = Future.sequence(
      body.files.sortBy(_.filename).map { file =>
        val fileExtension = FilenameUtils.getExtension(file.filename)
        val id            = BSONObjectID.generate()

        val fileInfo = FileInfo(
          id          = Some(id),
          name        = file.filename,
          extension   = fileExtension,
          contentType = file.contentType.getOrElse("unknown"),
          length      = file.ref.path.toFile.length,
          createTime  = DateTime.now,
          folderId    = folderId,
          isHidden    = false
        )
        val fileContentBytes: Array[Byte] = readAllBytes(file.ref.path)

        fileInfoService.upload(fileInfo, fileContentBytes)
          .flatMap {
            case Right(fileId) =>
              {
                if (handlePreResizes && fileInfo.isImage) {
                  imageTranscodingService.uploadPreResizesByFileId(
                    fileId = fileId
                  )
                } else {
                  Future.successful(())
                }
              }
                .map { _ =>
                  s"""{"id":"${fileId.stringify}", "label":"${fileInfo.name}", "url":"${if (
                      fileInfo.isImage
                    ) {
                      publicStorageUrls.publicImageUrl(id = fileId).absoluteURL()
                    } else {
                      publicStorageUrls.publicFileUrl(fileId).absoluteURL()
                    }}"}"""
                }

            case Left(errorMessage) =>
              val exception = new Throwable(errorMessage)
              Sentry.captureException(exception)
              Future.failed(exception)
          }
      }
    )

    resultsFuture
      .map { results =>
        Ok(results.mkString(start = "[", sep = ",", end = "]"))
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
      case Some(folderName) =>
        folderService.insert(Folder(id = None, name = folderName, parent = currentFolderIDOpt)).map(_ =>
          Ok("OK")
        )
      case _ => Future.successful(BadRequest)
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

  protected def renderInputStream(
      inputStream    : InputStream,
      contentLength  : Option[Long],
      contentType    : Option[String],
      fileName       : String,
      dispositionMode: String
  ): Result = {
    val res: Source[ByteString, Future[IOResult]] =
      StreamConverters
        .fromInputStream(() => inputStream)
        .idleTimeout(15.seconds)
        .initialTimeout(15.seconds)
        .completionTimeout(60.seconds)
        .recover { case e: Throwable =>
          val hint = new Hint
          hint.set("hint", "on stream recover")
          Sentry.captureException(e, hint)
          inputStream.close()
          ByteString.empty
        }

    // Just to make sure stream will be closed even if the file download is cancelled or not finished for any reason
    // Please note that this schedule will be cancelled once the stream is completed.
    val cancellable = actorSystem.scheduler.scheduleOnce(4.minutes) {
      try {
        inputStream.close()
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
          inputStream.close()
          cancellable.cancel()
          ByteString.empty
        }
      })

    val withCleanupRes = res.via(lazyFlow)

    val body = HttpEntity.Streamed.apply(
      data          = withCleanupRes,
      contentLength = contentLength,
      contentType   = contentType
    )

    Result(
      header = ResponseHeader(status = OK),
      body   = body
    ).withHeaders(
      CONTENT_LENGTH -> contentLength.map(_.toString).getOrElse(""),
      CONTENT_DISPOSITION ->
        (s"""$dispositionMode; filename="${java.net.URLEncoder
            .encode(fileName, "UTF-8")
            .replace("+", "%20")}"; filename*=UTF-8''""" +
        java.net.URLEncoder
          .encode(fileName, "UTF-8")
          .replace("+", "%20"))
    )

  }

  def renderFile(file: FileInfoService.FileObject, dispositionMode: String): Result =
    renderInputStream(
      inputStream     = file.content,
      contentLength   = file.contentLength,
      contentType     = file.contentType,
      fileName        = file.name,
      dispositionMode = dispositionMode
    )

  protected def renderFile(
      id                      : BSONObjectID,
      dispositionMode         : String,
      maybeUserDefinedFileName: Option[String]
  )(
      implicit requestHeader: RequestHeader
  ): Future[Result] = {
    fileInfoService.findObjectById(id).transform {

      case Success(file) =>
        val renamedFile =
          maybeUserDefinedFileName match {
            case None =>
              file
            case Some(userDefinedFileName) =>
              file.withUserDefinedName(userDefinedFileName)

          }
        Success(renderFile(renamedFile, dispositionMode))

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

}

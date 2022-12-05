package xcala.play.controllers

import xcala.play.services._
import xcala.play.models._
import play.api.cache.AsyncCacheApi
import play.api.Configuration
import akka.stream.Materializer
import play.api.mvc.Action
import play.api.mvc.AnyContent
import reactivemongo.api.bson._

import scala.concurrent.Future
import play.api.mvc.InjectedController
import play.api.mvc.Result
import play.api.i18n.{I18nSupport, Messages}
import play.api.libs.Files
import org.joda.time.DateTime

import java.nio.file.Files.readAllBytes
import play.api.libs.json.JsValue
import play.api.libs.json.Json
import play.api.mvc._
import org.apache.commons.io.FilenameUtils
import io.sentry.Sentry
import play.api.http.HttpEntity
import FileInfoService.FileObject

import scala.util.Success
import scala.util.Failure
import java.io.InputStream
import akka.stream.scaladsl.StreamConverters

import scala.concurrent.duration._
import com.sksamuel.scrimage.metadata.ImageMetadata
import com.sksamuel.scrimage.nio.GifWriter
import com.sksamuel.scrimage.nio.ImageWriter
import com.sksamuel.scrimage.nio.JpegWriter
import com.sksamuel.scrimage.nio.PngWriter
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.ScaleMethod
import io.sentry.Hint

import java.io.ByteArrayOutputStream
import akka.util.ByteString
import play.api.mvc.MultipartFormData
import xcala.play.utils.WithExecutionContext

trait FileControllerBase extends InjectedController with WithComposableActions with WithExecutionContext with I18nSupport {

  val fileInfoService: FileInfoService
  val folderService: FolderService
  val cache: AsyncCacheApi
  val configuration: Configuration
  implicit val mat: Materializer

  def defaultInternalServerError(implicit adminRequest: RequestType[_]): Result

  private val CONTENT_DISPOSITION_ATTACHMENT = "attachment"
  private val CONTENT_DISPOSITION_INLINE     = "inline"

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

  def uploadResult(folderId: Option[BSONObjectID], body: MultipartFormData[Files.TemporaryFile]): Future[Result] = {
    val resultsFuture = Future.sequence(
      body.files.map { file =>
        val fileExtension = FilenameUtils.getExtension(file.filename)
        val id            = BSONObjectID.generate

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

        fileInfoService.upload(fileInfo, readAllBytes(file.ref.path)).map {
          case Right(fileId) =>
            Ok(s"""{"id":"${fileId.stringify}", "label":"${fileInfo.name}"}""")
          case Left(e) =>
            InternalServerError(e)
        }
      }
    )

    resultsFuture.map { results =>
      results.headOption match {
        case Some(result) => result
        case None         => BadRequest
      }
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
      .recover { case _: Throwable =>
        defaultInternalServerError
      }
  }

  private def renderFile(id: BSONObjectID, dispositionMode: String): Future[Result] = {
    fileInfoService.findObjectById(id).map {
      case None       => NotFound
      case Some(file) => renderFile(file, dispositionMode)
    }
  }

  private def renderFile(file: FileObject, dispositionMode: String): Result = {
    val body = HttpEntity.Streamed.apply(file.content, file.contentLength, file.contentType)

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

  def getFile(id: BSONObjectID): Action[AnyContent] = Action.async {
    renderFile(id, CONTENT_DISPOSITION_ATTACHMENT)
  }

  def getImage(
      id: String,
      width: Option[Int],
      height: Option[Int]
  ): Action[AnyContent] = Action.async {
    BSONObjectID.parse(id.split('.').headOption.getOrElse(id)) match {
      case Success(bsonObjectId) =>
        fileInfoService.findObjectById(bsonObjectId).flatMap {
          case Some(file) if !file.isImage =>
            Future.successful(renderFile(file, CONTENT_DISPOSITION_INLINE))
          case Some(file) if file.isImage && width.isEmpty && height.isEmpty =>
            Future.successful(renderFile(file, CONTENT_DISPOSITION_INLINE))
          case Some(file) if file.isImage =>
            cache
              .getOrElseUpdate(s"image$id${width.getOrElse("")}${height.getOrElse("")}") {

                Future {
                  val is: InputStream = file.content.runWith(StreamConverters.asInputStream(120.seconds))

                  val image: ImmutableImage = ImmutableImage.loader().fromStream(is)

                  // Applying cleanups
                  is.close()

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
              }
              .transformWith {
                case Success(result) =>
                  Future.successful(result)
                case Failure(e) =>
                  Sentry.captureException(e)
                  Sentry.captureMessage("An Error has occurred while loading the image file: " + id)
                  Future.successful(InternalServerError)
              }

          case _ => Future.successful(NotFound)
        }
      case Failure(exception) =>
        val hint = new Hint
        hint.set("id", id)
        Sentry.captureException(exception)
        Future.successful(NotFound)
    }

  }

  private def renderImage(
      image: ImmutableImage,
      width: Option[Int],
      height: Option[Int],
      contentType: String,
      widthToHeightRatio: Double
  ): Result = {
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

    val body = HttpEntity.Strict(
      ByteString(bos.toByteArray),
      Some(contentType)
    )

    Result(header = ResponseHeader(200), body)
  }

  private def getImageWriter(contentType: String): ImageWriter = contentType match {
    case "image/gif" => GifWriter.Default
    case "image/png" => PngWriter.MinCompression
    case _           => JpegWriter.Default
  }

}

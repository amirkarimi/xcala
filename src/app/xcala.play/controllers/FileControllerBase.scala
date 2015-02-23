package xcala.play.controllers

import java.io.ByteArrayOutputStream

import com.sksamuel.scrimage.{Format, Image}
import com.sksamuel.scrimage.io.ImageWriter
import play.api.Play.current
import play.api.cache.Cached
import play.api.i18n.{Messages, Lang}
import play.api.libs.iteratee._
import play.api.libs.json.Json
import play.api.mvc._
import play.modules.reactivemongo.MongoController
import reactivemongo.api.gridfs.Implicits._
import reactivemongo.api.gridfs._
import reactivemongo.bson._
import xcala.play.models._
import xcala.play.services.FileService
import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

abstract class FileControllerBase(fileService: FileService) 
  extends Controller
  with WithComposableActions
  with MongoController 
  with WithoutImplicitLang 
  with WithExecutionContext {

  type RequestType[A] <: Request[A]

  def authenticatedAction[A](action: Action[A]): Action[A]

  def renameAction[A](action: Action[A]): Action[A]

  def deleteAction[A](action: Action[A]): Action[A]

  def selectorView(implicit request: RequestType[_], lang: Lang): Future[Result]

  def browserView(ckEditorFuncNum: Int, fileType: Option[String])(implicit request: RequestType[_], lang: Lang): Future[Result]

  def listView(files: List[FileEntry], folders: List[Folder], folderAndParents: List[Folder])(implicit request: RequestType[_], lang: Lang): Future[Result]

  /**
   * Returns the file matching the specified ID.
   */
  def getFile(id: BSONObjectID) = Action.async { implicit request =>
    serve(fileService.gridFS, fileService.gridFS.find(BSONDocument("_id" -> id)))
  }

  /**
   * Returns the image matching the specified ID and resize it to the specified width and height.
   *
   * It's OK to set one of the width or height.
   */
  def getImage(id: BSONObjectID, width: Option[Int], height: Option[Int]) = Cached("image" + id.stringify + width.getOrElse("") + height.getOrElse("")) {
    Action.async  { implicit request =>
      checkMaxResize(width, height)

	    fileService.getFile(id) flatMap {
	      case Some(file) if file.isImage && width.isEmpty && height.isEmpty =>
	        // Response original image
	        val oStream = new ByteArrayOutputStream(file.length)
	        fileService.gridFS.readToOutputStream(file, oStream) map { _ =>
	          Result(
	            header = ResponseHeader(200),
              body = Enumerator(oStream.toByteArray()))
          }

	      case Some(file) if file.isImage =>
	        val oStream = new ByteArrayOutputStream(file.length)
	        // Read file from GridFS
	        fileService.gridFS.readToOutputStream(file, oStream) map { _ =>
	          renderImage(oStream.toByteArray(), width, height, file.contentType)
	        }

	      case _ => Future.successful(NotFound)
	    }
  	}
  }

  private def checkMaxResize(width: Option[Int], height: Option[Int]) = {
    require(
      (for {
        width <- width
        max <- play.api.Play.application.configuration.getInt("file.image.maxResize.width")
      } yield width <= max).getOrElse(true)
    )
    require(
      (for {
        height <- height
        max <- play.api.Play.application.configuration.getInt("file.image.maxResize.height")
      } yield height <= max).getOrElse(true)
    )
  }

  private def renderImage(content: Array[Byte], width: Option[Int], height: Option[Int], contentType: Option[String]): Result = {
    val image = Image(content)
    val outImage = (width, height) match {
      case (Some(width), Some(height)) =>
        image.cover(width, height)
      case (Some(width), None) =>
        Image(content).scaleToWidth(width)
      case (None, Some(height)) =>
        Image(content).scaleToHeight(height)
      case _ =>
        throw new IllegalArgumentException()
    }

    val imageWriter = getImageWriter(contentType)

    val resultEnumerator = Enumerator.outputStream { outStream =>
	      try {
	        imageWriter(outImage).write(outStream)
	      } finally {
			    outStream.close
	      }
    }

    // Return result
    Result(
      header = ResponseHeader(200),
      body = resultEnumerator)
  }

  private def getImageWriter(contentType: Option[String]): Image => ImageWriter = contentType match {
    case Some("image/gif") => _.writer(Format.GIF)
    case Some("image/png") => _.writer(Format.PNG)
    case _ => _.writer(Format.JPEG).withCompression(90)
  }

  def selector = authenticatedAction {
    action { implicit request =>
      selectorView(request, implicitly)
    }
  }

  def browser(ckEditorFuncNum: Int, fileType: Option[String]) = authenticatedAction {
    action { implicit request =>
      browserView(ckEditorFuncNum, fileType)(request, Lang("fa"))
    }
  }

  def getList(folderId: Option[BSONObjectID], fileId: Option[BSONObjectID], fileType: Option[String]): Action[AnyContent] = authenticatedAction {
    action { implicit request =>
      val finalFolderId = fileId match {
        case None => Future.successful(folderId)
        case Some(fileId) => fileService.getFile(fileId).map(_.flatMap(_.folderId))
      }

      finalFolderId.flatMap(getList(_, fileType))
    }
  }

  private def getList(folderId: Option[BSONObjectID], fileType: Option[String])(implicit request: RequestType[_], lang: Lang): Future[Result] = {
    for {
      files <- fileService.getFilesUnderFolder(folderId, fileType)
      folders <- fileService.getFoldersUnderFolder(folderId)
      folderAndParents <- fileService.getFolderAndParents(folderId)

      realFolderAndParents = Folder(None, Messages("root"), None) :: folderAndParents
      result <- listView(files, folders, realFolderAndParents)
    } yield {
      result
    }
  }
  
  def upload(folderId: Option[BSONObjectID] = None) = authenticatedAction {
    action(parse.multipartFormData) { implicit request =>
      request.body.files.headOption map { filePart =>
        val gfs = fileService.gridFS
        val file = filePart.ref.file
        val enumerator = Enumerator.fromFile(file)
        val future = gfs.save(enumerator, DefaultFileToSave(filePart.filename, filePart.contentType))

        future flatMap { readFile =>
          fileService.setFileFolder(readFile, folderId) map { lastErrorOpt =>
            Ok(s"""{"id":"${stringify(readFile.id)}", "label":"${readFile.filename}"}""")
          }
        } 
      } getOrElse {
        Future.successful(BadRequest)
      }
	  }
  }
  
  def stringify(value: BSONValue) = value match {
    case objectID: BSONObjectID => objectID.stringify
    case other => other.toString
  }
  
  def createFolder = authenticatedAction {
    action(parse.json) { implicit request =>
	    val json = request.body
	    val folderNameOpt = (json \ "folderName").asOpt[String]
	    val currentFolderIDOpt = (json \ "currentFolderId").asOpt[String].flatMap(BSONObjectID.parse(_).toOption)
	    
	    folderNameOpt match {
	      case Some(folderName) => fileService.createFolder(Folder(None, folderName, currentFolderIDOpt)).map(_ => Ok("OK"))
	      case _ => Future.successful(BadRequest)
	    }
	  }
	}
  
  def getFileInfo = authenticatedAction {
    action(parse.json) { implicit request =>
      val input = request.body
      val idOpt = (input \ "id").asOpt[String].map(BSONObjectID.parse(_).toOption).flatten
      idOpt map { id => 
  	    fileService.getFile(id) map { 
  	      case None => Ok("{}")
  	      case Some(file) =>
  	        Ok(
              Json.obj(
  		          "filename" -> file.filename,
  		          "contentType" -> file.contentType
  	          )
            )
  	    }      
      } getOrElse {
        Future.successful(Ok("{}"))
      }
    }
  }
  
  def rename(id: BSONObjectID, itemType: String, newName: String) = renameAction {
    action { implicit request =>
      val future = itemType match {
        case "folder" =>
          fileService.renameFolder(id, newName)
        case "file" =>
          fileService.renameFile(id, newName)
      }
      
      future map { _ =>
        Ok("Ok")
      }
    }
  }
  
  def remove(id: BSONObjectID, itemType: String) = deleteAction {
    action { implicit request =>
      val future = itemType match {
        case "folder" =>
          fileService.removeFolder(id)
        case "file" =>
          fileService.removeFile(id)
      }
      
      future map { _ =>
        Ok("Ok")
      }
    }
  }
}

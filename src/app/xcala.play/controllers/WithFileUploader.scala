package xcala.play.controllers


import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import play.api.mvc._
import play.api.libs.Files.TemporaryFile
import play.api.libs.iteratee.Enumerator
import xcala.play.services.FileService
import reactivemongo.api.gridfs.Implicits._
import reactivemongo.api.gridfs._
import reactivemongo.bson._
import play.api.mvc.Result
import play.api.data.FormError
import xcala.play.utils.WithExecutionContext
import play.api.data.Form

object WithFileUploader {
  val AutoUploadSuffix = "_autoupload"
}

trait WithFileUploader extends WithExecutionContext {
  import WithFileUploader._
  
  protected val fileService: FileService
  
  def bindWithFiles[A](form: Form[A])(implicit request: Request[MultipartFormData[TemporaryFile]]): Future[Form[A]] = {
    val fileKeyValueFutures = request.body.files.filter(_.key.endsWith("." + AutoUploadSuffix)).map { filePart =>
      val fieldName = filePart.key.dropRight(AutoUploadSuffix.length + 1)
      
      val removeOldFileFuture = form.data.get(fieldName) match {
        case Some(oldValue) => fileService.removeFile(BSONObjectID.parse(oldValue).get)
        case None => Future.successful(None)
      }
      
      removeOldFileFuture flatMap { _ =>
        saveFile(filePart) map { fileId =>
          (fieldName -> fileId.stringify)
        }
      }
    }
    
    Future.sequence(fileKeyValueFutures) map { fileKeyValues =>
      val newData = form.data ++ fileKeyValues.toMap
      form.discardingErrors.bind(newData)
    }
  }
  
  def saveFile(key: String)(implicit request: Request[MultipartFormData[TemporaryFile]]): Future[Option[BSONObjectID]] = {
    request.body.file(key) match { 
      case None => Future.successful(None)
      case Some(filePart) => saveFile(filePart).map(Some(_))
    }
  }
  
  def saveFile(filePart: MultipartFormData.FilePart[TemporaryFile]): Future[BSONObjectID] = {
    val file = filePart.ref.file
    val enumerator = Enumerator.fromFile(file)
    val future = fileService.gridFS.save(enumerator, DefaultFileToSave(filePart.filename, filePart.contentType))

    future flatMap { readFile =>
      // Hide front-end user files from file manager
      fileService.setFileAsHidden(readFile) map { lastErrorOpt =>
        readFile.id.asInstanceOf[BSONObjectID]
      }
    }    
  }
}
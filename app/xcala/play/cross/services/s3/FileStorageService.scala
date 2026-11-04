package xcala.play.cross.services.s3

import xcala.play.cross.services.s3.FileStorageService.FileS3Object

import akka.NotUsed
import akka.stream.scaladsl.Source
import play.api.mvc.RequestHeader

import java.io.InputStream
import scala.concurrent.Future

object FileStorageService {

  final case class FileS3Object(
      objectName   : String,
      originalName : String,
      content      : InputStream,
      contentType  : Option[String],
      contentLength: Option[Long],
      path         : Option[String]
  )

}

trait FileStorageService {

  def config         : play.api.Configuration
  lazy val bucketName: String = config.get[String]("fileStorage.s3.bucketName")

  def upload(
      objectName  : String,
      content     : Array[Byte],
      contentType : String,
      originalName: String,
      path        : Option[String] = None
  )(implicit
      requestHeader: RequestHeader
  ): Future[Boolean]

  /** Read file from s3
    *
    * @param objectName
    *   String, name of file in s3. Ex: picture.jpg
    * @param path
    *   String, directory of the file. Ex: first/second/thirdFolder/
    * @return
    */
  def findByObjectName(objectName: String, path: Option[String] = None)(implicit
      requestHeader: RequestHeader
  ): Future[FileS3Object]

  /** Delete file by name and path
    *
    * @param objectName
    *   String, file name, in our case it is fileEntity UUID
    * @param path
    *   String Ex: folder1/folder2/
    * @return
    */
  def deleteByObjectName(objectName: String, path: Option[String] = None)(implicit
      requestHeader: RequestHeader
  ): Future[Boolean]

  def getList(path: Option[String] = None)(implicit
      requestHeader: RequestHeader
  ): Future[List[String]]

  def filesStream(path: Option[String] = None): Source[String, NotUsed]

  /** Clean deformed path Ex: Some("folder1/") => folder1/ Some("folder1") => folder1/ Some("") => "" None =>
    * ""
    *
    * @param path
    *   Option[String]
    * @return
    */
  protected def getCleanPath(path: Option[String]): String = {
    path match {
      case Some(s) if s.nonEmpty && s.last == '/' => s
      case Some(s) if s.nonEmpty                  => s.concat("/")
      case _                                      => ""
    }
  }

}

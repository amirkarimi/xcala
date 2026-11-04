package xcala.play.cross.services

import xcala.play.cross.services.s3.FileStorageService

import play.api.mvc.RequestHeader

import scala.concurrent.Future

trait CrossFileInfoService[Id, FI] {

  val fileStorageService: FileStorageService

  def removeFile(id: Id)(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Int]]

  def upload(fileEntity: FI, content: Array[Byte])(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Id]]

}

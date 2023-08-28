package xcala.play.utils

import xcala.play.models.ProtectedFileSignatureParameters
import xcala.play.models.ProtectedImageSignatureParameters
import xcala.play.models.PublicFileSignatureParameters
import xcala.play.models.PublicImageSignatureParameters

import play.api.Configuration
import play.api.mvc.Call

import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

sealed trait BaseStorageUrls

object BaseStorageUrls {

  sealed trait ProtectedStorageUrls extends BaseStorageUrls {

    def protectedImageUrl(
        id: BSONObjectID,
        width: Option[Int] = None,
        height: Option[Int] = None
    ): Call

    def protectedFileUrl(
        id: BSONObjectID
    ): Call

  }

  sealed trait StorageSignedUrls extends BaseStorageUrls {
    implicit def configuration: Configuration
  }

  trait ProtectedStorageUnsignedUrls extends ProtectedStorageUrls

  trait ProtectedStorageSignedUrls extends ProtectedStorageUrls with StorageSignedUrls {

    protected def protectedImageUrl(
        id: BSONObjectID,
        signature: String,
        width: Option[Int],
        height: Option[Int],
        expireTime: Long
    ): Call

    def protectedImageUrl(
        id: BSONObjectID,
        width: Option[Int] = None,
        height: Option[Int] = None
    ): Call = {
      val minutesToBeExpiredAfter = configuration.get[Int]("fileStorage.s3.timeLimitedUrl.urlToBeExpiredAfterMinutes")
      val expireTime              = DateTime.now().plusMinutes(minutesToBeExpiredAfter)
      val signature: String       = ProtectedImageSignatureParameters(id, expireTime).signature
      protectedImageUrl(
        id = id,
        signature = signature,
        width = width,
        height = height,
        expireTime = expireTime.getMillis
      )
    }

    protected def protectedFileUrl(
        id: BSONObjectID,
        signature: String,
        expireTime: Long
    ): Call

    def protectedFileUrl(
        id: BSONObjectID
    ): Call = {
      val minutesToBeExpiredAfter = configuration.get[Int]("fileStorage.s3.timeLimitedUrl.urlToBeExpiredAfterMinutes")
      val expireTime              = DateTime.now().plusMinutes(minutesToBeExpiredAfter)
      val signature: String       = ProtectedFileSignatureParameters(id, expireTime).signature
      protectedFileUrl(id = id, signature = signature, expireTime = expireTime.getMillis)
    }

  }

  sealed trait PublicStorageUrls extends BaseStorageUrls {

    def publicImageUrl(
        id: BSONObjectID,
        width: Option[Int] = None,
        height: Option[Int] = None,
        extension: Option[String] = None
    ): Call

    def publicFileUrl(
        id: BSONObjectID
    ): Call

  }

  trait PublicStorageUnsignedUrls extends PublicStorageUrls

  trait PublicStorageSignedUrls extends PublicStorageUrls with StorageSignedUrls {

    protected def publicImageUrl(
        id: BSONObjectID,
        signature: String,
        width: Option[Int],
        height: Option[Int],
        extension: Option[String]
    ): Call

    def publicImageUrl(
        id: BSONObjectID,
        width: Option[Int] = None,
        height: Option[Int] = None,
        extension: Option[String] = None
    ): Call = {
      val signature: String = PublicImageSignatureParameters(id).signature
      publicImageUrl(id = id, signature = signature, width = width, height = height, extension = extension)
    }

    protected def publicFileUrl(
        id: BSONObjectID,
        signature: String
    ): Call

    def publicFileUrl(
        id: BSONObjectID
    ): Call = {
      val signature: String = PublicFileSignatureParameters(id).signature
      publicFileUrl(id, signature)
    }

  }

}

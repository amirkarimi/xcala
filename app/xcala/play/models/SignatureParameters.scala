package xcala.play.models

import play.api.Configuration

import org.apache.commons.codec.digest.HmacAlgorithms.HMAC_SHA_512
import org.apache.commons.codec.digest.HmacUtils
import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID

sealed abstract class SignatureParameters(implicit val configuration: Configuration) {

  val id: BSONObjectID

  def asRawText(): String

  lazy val signature: String =
    new HmacUtils(HMAC_SHA_512, configuration.get[String]("urlSignatureSecret")).hmacHex(asRawText())

  def isValid(incomingSignature: String): Boolean =
    signature == incomingSignature

}

sealed abstract class ImageSignatureParameters(
    id         : BSONObjectID,
    isProtected: Boolean,
    expiryTime : Option[DateTime]
)(
    implicit configuration: Configuration
) extends SignatureParameters {

  def asRawText(): String =
    s"${id.stringify}-image-${if (isProtected) "protected" else "public"}-${expiryTime.map(_.getMillis.toString).mkString}"

  override def isValid(incomingSignature: String): Boolean =
    super.isValid(incomingSignature) && expiryTime.forall(_.isAfterNow())

}

sealed abstract class FileSignatureParameters(
    id         : BSONObjectID,
    isProtected: Boolean,
    expiryTime : Option[DateTime]
)(
    implicit configuration: Configuration
) extends SignatureParameters {
  def asRawText(): String = s"${id.stringify}-file-${if (isProtected) "protected" else "public"}"

  override def isValid(incomingSignature: String): Boolean =
    super.isValid(incomingSignature) && expiryTime.forall(_.isAfterNow())

}

final case class PublicImageSignatureParameters(id: BSONObjectID)(implicit configuration: Configuration)
    extends ImageSignatureParameters(id = id, isProtected = false, expiryTime = None)

final case class PublicFileSignatureParameters(id: BSONObjectID)(implicit configuration: Configuration)
    extends FileSignatureParameters(id = id, isProtected = false, expiryTime = None)

final case class ProtectedImageSignatureParameters(id: BSONObjectID, expireTime: DateTime)(implicit
    configuration: Configuration
) extends ImageSignatureParameters(id = id, isProtected = true, expiryTime = Some(expireTime))

final case class ProtectedFileSignatureParameters(id: BSONObjectID, expireTime: DateTime)(implicit
    configuration: Configuration
) extends FileSignatureParameters(id = id, isProtected = true, expiryTime = Some(expireTime))

package xcala.play.cross.models

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

final case class ImageTranscodingRequest(
    id                            : UUID,
    objectName                    : String,
    bucketName                    : String,
    targetWidthToResizedImageNames: Map[Int, String],
    fileOriginalName              : String,
    resultTopic                   : String
) extends ImageTranscodingMessage {

  def makeSuccessfulResponse(): ImageTranscodingResponse =
    ImageTranscodingResponse(
      id    = id,
      error = None
    )

  def makeFailedResponse(errorMessage: String): ImageTranscodingResponse =
    ImageTranscodingResponse(
      id    = id,
      error = Some(errorMessage)
    )

}

object ImageTranscodingRequest {

  def create(
      objectName                    : String,
      bucketName                    : String,
      targetWidthToResizedImageNames: Map[Int, String],
      fileOriginalName              : String,
      resultTopic                   : String
  ): ImageTranscodingRequest =
    ImageTranscodingRequest(
      id                             = UUID.randomUUID(),
      objectName                     = objectName,
      bucketName                     = bucketName,
      targetWidthToResizedImageNames = targetWidthToResizedImageNames,
      fileOriginalName               = fileOriginalName,
      resultTopic                    = resultTopic
    )

  implicit val formatter: OFormat[ImageTranscodingRequest] =
    Json.format[ImageTranscodingRequest]

}

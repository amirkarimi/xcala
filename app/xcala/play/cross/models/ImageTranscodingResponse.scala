package xcala.play.cross.models

import play.api.libs.json.Json
import play.api.libs.json.OFormat

import java.util.UUID

final case class ImageTranscodingResponse(
    id   : UUID,
    error: Option[String]
) extends ImageTranscodingMessage

object ImageTranscodingResponse {

  implicit val formatter: OFormat[ImageTranscodingResponse] =
    Json.format[ImageTranscodingResponse]

}

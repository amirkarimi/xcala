package xcala.play.models

import play.api.libs.json.Json
import play.api.libs.json.JsResult
import play.api.libs.json.Reads
import play.api.libs.json.Writes

import scala.util.Try

trait TaskBased {
  type ResultType

  def serializeResult(result: ResultType): String

  def deserializeResult(serializedResult: String): ResultType
}

abstract class TaskBasedWithEither[A, B](
    implicit
    val aWrites: Writes[A],
    val bWrites: Writes[B],
    val aReads : Reads[A],
    val bReads : Reads[B]
) extends TaskBased {
  type ResultType = Either[A, B]

  implicit val eitherWrites: Writes[Either[A, B]] =
    Writes {
      case Left(a)  => Json.obj("Left" -> aWrites.writes(a))
      case Right(b) => Json.obj("Right" -> bWrites.writes(b))
    }

  implicit val eitherReads: Reads[Either[A, B]] =
    Reads { jsValue =>
      JsResult.fromTry(
        Try {
          (jsValue \ "Left")
            .asOpt[A].map(Left(_))
            .getOrElse(
              Right((jsValue \ "Right").as[B])
            )
        }
      )
    }

  def serializeResult(result: ResultType): String =
    Json.toJson(result).toString()

  def deserializeResult(serializedResult: String): ResultType =
    Json.fromJson[ResultType](Json.parse(serializedResult)).get

}

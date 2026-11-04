package xcala.play.extensions

import play.api.data._
import play.api.data.format.Formats._
import play.api.data.format.Formatter
import play.api.libs.json._

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import reactivemongo.api.bson.BSONObjectID

object Formats {

  /*
   * Copied from Play framework repository.
   */
  private def parsing[T](parse: String => Try[T], errMsg: String, errArgs: Seq[Any])(
      key : String,
      data: Map[String, String]
  ): Either[Seq[FormError], T] = {
    stringFormat.bind(key, data).flatMap { s =>
      parse(s).toEither.left
        .map(_ => Seq(FormError(key = key, message = errMsg, args = errArgs)))
    }
  }

  implicit val bsonObjectIDFormatter: Formatter[BSONObjectID] = new Formatter[BSONObjectID] {

    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], BSONObjectID] = {
      parsing(parse = BSONObjectID.parse, errMsg = "error.objectId", errArgs = Nil)(key, data)
    }

    def unbind(key: String, value: BSONObjectID): Map[String, String] = Map(key -> value.stringify)
  }

  implicit val bsonObjectIDFormat: Format[BSONObjectID] = new Format[BSONObjectID] {

    def reads(json: JsValue): JsResult[BSONObjectID] = json match {
      case JsString(s) =>
        BSONObjectID.parse(s) match {
          case Success(d) => JsSuccess(d)
          case Failure(_) =>
            JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.objectId.format"))))
        }
      case _ => JsError(Seq(JsPath() -> Seq(JsonValidationError("error.expected.objectId"))))
    }

    def writes(o: BSONObjectID): JsValue = JsString(o.stringify)
  }

}

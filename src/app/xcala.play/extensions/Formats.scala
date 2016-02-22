package xcala.play.extensions

import scala.util.{ Success, Failure }
import play.api.data.Forms._
import play.api.data._
import play.api.data.format.Formats._
import play.api.data.format.Formatter
import play.api.data.validation._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

object Formats {

  /*
   * Copied from Play framework repository.
   */
  private def parsing[T](parse: String => T, errMsg: String, errArgs: Seq[Any])(key: String, data: Map[String, String]): Either[Seq[FormError], T] = {
    stringFormat.bind(key, data).right.flatMap { s =>
      scala.util.control.Exception.allCatch[T]
        .either(parse(s))
        .left.map(e => Seq(FormError(key, errMsg, errArgs)))
    }
  }

  implicit val bsonObjectIDFormatter = new Formatter[BSONObjectID] {
    def bind(key: String, data: Map[String, String]) = {
      parsing(BSONObjectID.parse(_).get, "error.objectId", Nil)(key, data)
    }

    def unbind(key: String, value: BSONObjectID) = Map(key -> value.stringify)
  }

  implicit val bsonObjectIDFormat = new Format[BSONObjectID] {
    def reads(json: JsValue): JsResult[BSONObjectID] = json match {
      case JsString(s) => BSONObjectID.parse(s) match {
        case Success(d) => JsSuccess(d)
        case Failure(e) => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.objectId.format"))))
      }
      case _ => JsError(Seq(JsPath() -> Seq(ValidationError("error.expected.objectId"))))
    }

    def writes(o: BSONObjectID): JsValue = JsString(o.stringify)
  }
}
package xcala.play.postgres.utils

import play.api.libs.json._

import scala.util.Try

import org.joda.time.{Duration, LocalTime}

object JsFormatters {

  implicit object LocalTimeFormatter extends Format[LocalTime] {
    val errorMsg: String = "Invalid time format!"

    def reads(json: JsValue): JsResult[LocalTime] = {
      json match {
        case JsString(value) => Try(LocalTime.parse(value)).map(JsSuccess(_)).getOrElse(JsError(errorMsg))
        case _               => JsError(errorMsg)
      }
    }

    def writes(o: LocalTime): JsValue = JsString(o.toString("HH:mm:ss"))
  }

  implicit object DurationFormatter extends Format[Duration] {
    val errorMsg: String = "Invalid duration format!"

    def reads(json: JsValue): JsResult[Duration] = {
      json match {
        case JsNumber(value) => Try(new Duration(value.toLong)).map(JsSuccess(_)).getOrElse(JsError(errorMsg))
        case JsString(value) => Try(Duration.parse(value)).map(JsSuccess(_)).getOrElse(JsError(errorMsg))
        case _               => JsError(errorMsg)
      }
    }

    def writes(o: Duration): JsValue = JsNumber(o.getMillis)
  }

}

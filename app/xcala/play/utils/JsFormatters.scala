package xcala.play.utils

import play.api.libs.json._
import play.api.libs.json.JodaReads._
import play.api.libs.json.JodaWrites._

import scala.util.Try

import org.joda.time.DateTime
import org.joda.time.Duration
import org.joda.time.LocalDate
import org.joda.time.LocalTime

object JsFormatters {

  implicit object DateTimeFormatter extends Format[DateTime] {
    def reads(json: JsValue) : JsResult[DateTime] = JodaDateReads.reads(json)
    def writes(o  : DateTime): JsValue            = JodaDateTimeNumberWrites.writes(o)
  }

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

  implicit object LocalDateFormatter extends Format[LocalDate] {
    val errorMsg: String = "Invalid date format!"

    def reads(json: JsValue): JsResult[LocalDate] = {
      json match {
        case JsString(value) => Try(LocalDate.parse(value)).map(JsSuccess(_)).getOrElse(JsError(errorMsg))
        case _               => JsError(errorMsg)
      }
    }

    def writes(o: LocalDate): JsValue = JsString(o.toString)
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

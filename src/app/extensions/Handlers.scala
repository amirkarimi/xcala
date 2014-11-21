package xcala.play.extensions

import reactivemongo.bson._
import org.joda.time.DateTime

object Handlers {
  implicit object BSONDateTimeHandler extends BSONHandler[BSONDateTime, DateTime] {
    def read(time: BSONDateTime) = new DateTime(time.value)
    def write(jdtime: DateTime) = BSONDateTime(jdtime.getMillis)
  }

  implicit object BigDecimalHandler extends BSONHandler[BSONValue, BigDecimal] {
    def read(value: BSONValue) = value match {
      case d: BSONDouble => BigDecimal(d.value)
      case d: BSONInteger => BigDecimal(d.value)
      case d: BSONLong => BigDecimal(d.value)
    }
    def write(value: BigDecimal) = new BSONDouble(value.toDouble)
  }
}

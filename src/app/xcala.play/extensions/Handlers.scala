package xcala.play.extensions

import reactivemongo.bson._
import org.joda.time.DateTime
import xcala.play.models.Range

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

  implicit def rangeHandler[A](implicit handler: BSONHandler[_ <: BSONValue, A]) = new BSONHandler[BSONValue, Range[A]] {
    def read(value: BSONValue) = value match {
      case doc: BSONDocument => new Range(from = doc.getAs[A]("from").get, to = doc.getAs[A]("to").get)
    }
    
    def write(range: Range[A]) = BSONDocument("from" -> range.from, "to" -> range.to)
  }
}

package xcala.play.extensions

import reactivemongo.bson._
import org.joda.time.DateTime
import xcala.play.models.Range

object BSONHandlers {
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

  implicit def optionalRangeHandler[A](implicit handler: BSONHandler[_ <: BSONValue, A]) = new BSONDocumentReader[Range[Option[A]]] with BSONDocumentWriter[Range[Option[A]]] {
    def read(doc: BSONDocument) = {
      new Range(from = doc.getAs[A]("from"), to = doc.getAs[A]("to"))
    }
    def write(range: Range[Option[A]]) = {
      BSONDocument(
        Seq(
          range.from.flatMap(handler.writeOpt(_)).map("from" -> _), 
          range.to.flatMap(handler.writeOpt(_)).map("to" -> _)
        ).flatten
      )
    }
  }

  implicit def rangeHandler[A](implicit handler: BSONHandler[_ <: BSONValue, A]) = new BSONDocumentReader[Range[A]] with BSONDocumentWriter[Range[A]] {
    def read(doc: BSONDocument) = {
      new Range(from = doc.getAs[A]("from").get, to = doc.getAs[A]("to").get)
    }
    def write(range: Range[A]) = {
      BSONDocument(
        Seq(
          handler.writeOpt(range.from).map("from" -> _), 
          handler.writeOpt(range.to).map("to" -> _)
        ).flatten
      )
    }
  }
  
  implicit def optionHandler[A <: BSONValue, B](implicit handler: BSONHandler[A, B]) = new BSONHandler[A, Option[B]] {
    def read(value: A): Option[B] = handler.readOpt(value)
    def write(value: Option[B]): A = handler.write(value.get)
  }
}

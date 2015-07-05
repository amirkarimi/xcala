package xcala.play.extensions

import reactivemongo.bson._
import org.joda.time.DateTime
import xcala.play.models.{MultilangModel, Range}

import scala.util.Try

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

  implicit def multilangDocumentHandler[A <: BSONValue] = new BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] {
    def read(doc: BSONDocument): MultilangModel[A] = {
      MultilangModel(lang = doc.getAs[String]("lang").get, value = doc.get("value").get.asInstanceOf[A])
    }
    def write(multilangModel: MultilangModel[A]): BSONDocument = {
      BSONDocument(
        Seq(
          "lang" -> BSONString(multilangModel.lang),
          "value" -> multilangModel.value
        )
      )
    }
  }

  implicit def optionalMultilangDocumentHandler[A <: BSONValue] = new BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] {
    def read(doc: BSONDocument): MultilangModel[Option[A]] = {
      MultilangModel(lang = doc.getAs[String]("lang").get, value = doc.get("value").collect({case v: A => v}))
    }
    def write(multilangModel: MultilangModel[Option[A]]): BSONDocument = {
      BSONDocument(
        Seq(
          Some("lang" -> BSONString(multilangModel.lang)),
          multilangModel.value.map("value" -> _)
        ).flatten
      )
    }
  }

  implicit def multilangHandler[A](implicit handler: BSONHandler[_ <: BSONValue, A]) = new BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] {
    def read(doc: BSONDocument): MultilangModel[A] = {
      MultilangModel(lang = doc.getAs[String]("lang").get, value = doc.getAs[A]("value").get)
    }
    def write(multilangModel: MultilangModel[A]): BSONDocument = {
      BSONDocument(
        Seq(
          Some(("lang" -> BSONString(multilangModel.lang))),
          handler.writeOpt(multilangModel.value).map("value" -> _)
        ).flatten
      )
    }
  }

  implicit def optionalMultilangHandler[A](implicit handler: BSONHandler[_ <: BSONValue, A]) = new BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] {
    def read(doc: BSONDocument) = {
      MultilangModel(lang = doc.getAs[String]("lang").get, value = doc.getAs[A]("value"))
    }
    def write(multilangModel: MultilangModel[Option[A]]): BSONDocument = {
      BSONDocument(
        Seq(
          Some(("lang" -> BSONString(multilangModel.lang))),
          multilangModel.value.flatMap(handler.writeOpt(_)).map("value" -> _)
        ).flatten
      )
    }
  }
}

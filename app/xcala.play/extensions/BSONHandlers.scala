package xcala.play.extensions

import reactivemongo.api.bson._
import org.joda.time.{DateTime, LocalDate}
import xcala.play.models.{MultilangModel, Range}

import scala.util.{Failure, Success, Try}

object BSONHandlers {
  implicit object BSONLocalDateHandler extends BSONHandler[LocalDate] {
    def readTry(v: BSONValue) = v match {
      case BSONDateTime(dateTime) => Success(new LocalDate(dateTime))
      case _ => Failure(new IllegalArgumentException())
    }

    def writeTry(localDate: LocalDate) = Try(BSONDateTime(localDate.toDateTimeAtStartOfDay().getMillis))
  }


  implicit object DateTimeReader extends BSONReader[DateTime] {
    def readTry(bson: BSONValue): Try[DateTime] = bson match {
      case time: BSONDateTime => Success(new DateTime(time.value))
      case _ => Failure(new IllegalArgumentException())
    }
  }

  implicit object DateTimeWriter extends BSONWriter[DateTime] {
    override def writeTry(t: DateTime): Try[BSONValue] = Try {
      BSONDateTime(t.getMillis)
    }
  }

  implicit def optionalRangeHandler[A](implicit handler: BSONHandler[A]) = new BSONDocumentReader[Range[Option[A]]] with BSONDocumentWriter[Range[Option[A]]] {
    def readDocument(doc: BSONDocument): Try[Range[Option[A]]] = Success {
      Range(from = doc.getAsOpt[A]("from"), to = doc.getAsOpt[A]("to"))
    }

    def writeTry(range: Range[Option[A]]) = Try {
      BSONDocument(
        Seq(
          range.from.flatMap(handler.writeOpt).map("from" -> _),
          range.to.flatMap(handler.writeOpt).map("to" -> _)
        ).flatten
      )
    }
  }

  implicit def rangeHandler[A](implicit handler: BSONHandler[A]) = new BSONDocumentReader[Range[A]] with BSONDocumentWriter[Range[A]] {
    def readDocument(doc: BSONDocument): Try[Range[A]] = {
      (doc.getAsOpt[A]("from"), doc.getAsOpt[A]("to")) match {
        case (Some(from), Some(to)) => Success(Range(from = from, to = to))
        case _ => Failure(new NoSuchFieldException())
      }
    }

    def writeTry(range: Range[A]) = Try {
      BSONDocument(
        Seq(
          handler.writeOpt(range.from).map("from" -> _),
          handler.writeOpt(range.to).map("to" -> _)
        ).flatten
      )
    }
  }

//  implicit def optionHandler[A <: BSONValue, B](implicit handler: BSONHandler[B]) = new BSONHandler[Option[B]] {
//    def readTry(value: BSONValue): Try[Option[B]] = Success(handler.readOpt(value))
//
//    def writeTry(value: Option[B]): Try[BSONValue] = handler.writeTry(value.get)
//  }

  implicit def multilangDocumentHandler[A <: BSONValue] = new BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] {
    def readDocument(doc: BSONDocument): Try[MultilangModel[A]] = {
      (doc.getAsOpt[String]("lang"), doc.get("value")) match {
        case (Some(lang), Some(value)) => Success(
          MultilangModel(lang = lang, value = value.asInstanceOf[A])
        )
        case _ => Failure(new NoSuchFieldException())
      }
    }

    def writeTry(multilangModel: MultilangModel[A]): Try[BSONDocument] = Try {
      BSONDocument(
        Seq(
          "lang" -> BSONString(multilangModel.lang),
          "value" -> multilangModel.value
        )
      )
    }
  }

  implicit def optionalMultilangDocumentHandler[A <: BSONValue] = new BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] {
    def readDocument(doc: BSONDocument): Try[MultilangModel[Option[A]]] = {
      (doc.getAsOpt[String]("lang"), doc.get("value")) match {
        case (Some(lang), value) => Success(
          MultilangModel(lang = lang, value = value.collect({ case v: A => v }))
        )
        case _ => Failure(new NoSuchFieldException())
      }
    }

    def writeTry(multilangModel: MultilangModel[Option[A]]): Try[BSONDocument] = Try {
      BSONDocument(
        Seq(
          Some("lang" -> BSONString(multilangModel.lang)),
          multilangModel.value.map("value" -> _)
        ).flatten
      )
    }
  }

  implicit def multilangHandler[A](implicit handler: BSONHandler[A]) = new BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] {
    def readDocument(doc: BSONDocument): Try[MultilangModel[A]] = {
      (doc.getAsOpt[String]("lang"), doc.getAsOpt[A]("value")) match {
        case (Some(lang), Some(value)) => Success(
          MultilangModel(lang = lang, value = value)
        )
        case _ => Failure(new NoSuchFieldException())
      }
    }

    def writeTry(multilangModel: MultilangModel[A]): Try[BSONDocument] = Try {
      BSONDocument(
        Seq(
          Some("lang" -> BSONString(multilangModel.lang)),
          handler.writeOpt(multilangModel.value).map("value" -> _)
        ).flatten
      )
    }
  }

  implicit def optionalMultilangHandler[A](implicit handler: BSONHandler[A]) = new BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] {
    def readDocument(doc: BSONDocument) = {
      (doc.getAsOpt[String]("lang"), doc.getAsOpt[A]("value")) match {
        case (Some(lang), value) => Success(
          MultilangModel(lang = lang, value = value)
        )
        case _ => Failure(new NoSuchFieldException())
      }
    }

    def writeTry(multilangModel: MultilangModel[Option[A]]): Try[BSONDocument] = Try {
      BSONDocument(
        Seq(
          Some("lang" -> BSONString(multilangModel.lang)),
          multilangModel.value.flatMap(handler.writeOpt).map("value" -> _)
        ).flatten
      )
    }
  }
}

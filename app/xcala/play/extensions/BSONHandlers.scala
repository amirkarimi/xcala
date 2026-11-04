package xcala.play.extensions

import xcala.play.models.MultilangModel
import xcala.play.models.Range

import scala.reflect.ClassTag
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.joda.time.DateTime
import org.joda.time.LocalDate
import reactivemongo.api.bson._

object BSONHandlers {

  def bigIntBSONHandler(scale: Int, roundingType: BigDecimal.RoundingMode.Value): BSONHandler[BigInt] =
    new BSONHandler[BigInt] {

      val bigDecimalBSONHandler: BSONHandler[BigDecimal] =
        implicitly[BSONHandler[BigDecimal]]

      def readTry(v: BSONValue): Try[BigInt] =
        bigDecimalBSONHandler
          .readTry(v)
          .map(_.setScale(scale, roundingType).toBigInt)

      def writeTry(bigInt: BigInt): Try[BSONValue] =
        bigDecimalBSONHandler.writeTry(BigDecimal(bigInt))

    }

  implicit object BSONLocalDateHandler extends BSONHandler[LocalDate] {

    def readTry(v: BSONValue): Try[LocalDate] = v match {
      case BSONDateTime(dateTime) => Success(new LocalDate(dateTime))
      case _                      => Failure(new IllegalArgumentException())
    }

    def writeTry(localDate: LocalDate): Try[BSONDateTime] = Try(
      BSONDateTime(localDate.toDateTimeAtStartOfDay().getMillis)
    )

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

  implicit def optionalRangeHandler[A](implicit
      handler: BSONHandler[A]
  ): BSONDocumentReader[Range[Option[A]]] with BSONDocumentWriter[Range[Option[A]]] =
    new BSONDocumentReader[Range[Option[A]]] with BSONDocumentWriter[Range[Option[A]]] {

      def readDocument(doc: BSONDocument): Try[Range[Option[A]]] = Success {
        Range(from = doc.getAsOpt[A]("from"), to = doc.getAsOpt[A]("to"))
      }

      def writeTry(range: Range[Option[A]]): Try[BSONDocument] = Try {
        BSONDocument(
          Seq(
            range.from.flatMap(handler.writeOpt).map("from" -> _),
            range.to.flatMap(handler.writeOpt).map("to" -> _)
          ).flatten
        )
      }

    }

  implicit def rangeHandler[A](implicit
      handler: BSONHandler[A]
  ): BSONDocumentReader[Range[A]] with BSONDocumentWriter[Range[A]] = new BSONDocumentReader[Range[A]]
    with BSONDocumentWriter[Range[A]] {

    def readDocument(doc: BSONDocument): Try[Range[A]] = {
      (doc.getAsOpt[A]("from"), doc.getAsOpt[A]("to")) match {
        case (Some(from), Some(to)) => Success(Range(from = from, to = to))
        case _                      => Failure(new NoSuchFieldException())
      }
    }

    def writeTry(range: Range[A]): Try[BSONDocument] = Try {
      BSONDocument(
        Seq(
          handler.writeOpt(range.from).map("from" -> _),
          handler.writeOpt(range.to).map("to" -> _)
        ).flatten
      )
    }

  }

  implicit def multilangDocumentHandler[A <: BSONValue]
      : BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] =
    new BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] {

      def readDocument(doc: BSONDocument): Try[MultilangModel[A]] = {
        (doc.getAsOpt[String]("lang"), doc.get("value")) match {
          case (Some(lang), Some(value)) =>
            Success(
              MultilangModel(lang = lang, value = value.asInstanceOf[A])
            )
          case _ => Failure(new NoSuchFieldException())
        }
      }

      def writeTry(multilangModel: MultilangModel[A]): Try[BSONDocument] = Try {
        BSONDocument(
          Seq(
            "lang"  -> BSONString(multilangModel.lang),
            "value" -> multilangModel.value
          )
        )
      }

    }

  implicit def optionalMultilangDocumentHandler[A <: BSONValue](implicit
      classTag: ClassTag[A]
  ): BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] =
    new BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] {

      def readDocument(doc: BSONDocument): Try[MultilangModel[Option[A]]] = {
        (doc.getAsOpt[String]("lang"), doc.get("value")) match {
          case (Some(lang: String), value: Option[BSONValue]) =>
            Success(
              MultilangModel(
                lang  = lang,
                value = value.collect { case x: A => x }
              ) // implicit classTag defined above helps with erasure bug here
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

  implicit def multilangHandler[A](implicit
      handler: BSONHandler[A]
  ): BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] =
    new BSONDocumentReader[MultilangModel[A]] with BSONDocumentWriter[MultilangModel[A]] {

      def readDocument(doc: BSONDocument): Try[MultilangModel[A]] = {
        (doc.getAsOpt[String]("lang"), doc.getAsOpt[A]("value")) match {
          case (Some(lang), Some(value)) =>
            Success(
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

  implicit def optionalMultilangHandler[A](implicit
      handler: BSONHandler[A]
  ): BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] =
    new BSONDocumentReader[MultilangModel[Option[A]]] with BSONDocumentWriter[MultilangModel[Option[A]]] {

      def readDocument(doc: BSONDocument): Try[MultilangModel[Option[A]]] = {
        (doc.getAsOpt[String]("lang"), doc.getAsOpt[A]("value")) match {
          case (Some(lang), value) =>
            Success(
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

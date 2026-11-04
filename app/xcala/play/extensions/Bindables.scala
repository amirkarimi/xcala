package xcala.play.extensions

import play.api.i18n.Lang
import play.api.mvc.JavascriptLiteral
import play.api.mvc.PathBindable
import play.api.mvc.QueryStringBindable

import org.joda.time.{DateTime, LocalDate}
import reactivemongo.api.bson.BSONObjectID

object Bindables {

  implicit def jodaDateTimeBinder: QueryStringBindable[DateTime] = new QueryStringBindable[DateTime] {

    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, DateTime]] = {
      try {
        Some(Right(new DateTime(params(key).head.toLong)))
      } catch {
        case _: IllegalArgumentException | _: NumberFormatException =>
          Some(Left("Invalid date time format. Use a long number as milliseconds since midnight Jan 1, 1970"))
      }
    }

    def unbind(key: String, value: DateTime): String = key + "=" + value.getMillis.toString
  }

  implicit def jodaLocalDateBinder: QueryStringBindable[LocalDate] = new QueryStringBindable[LocalDate] {

    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, LocalDate]] = {
      try {
        Some(Right(new LocalDate(params(key).head)))
      } catch {
        case _: IllegalArgumentException | _: NumberFormatException => Some(Left("Invalid local date format"))
      }
    }

    def unbind(key: String, value: LocalDate): String = key + "=" + value.toString
  }

  implicit def jodaOptionalLocalDateBinder: QueryStringBindable[Option[LocalDate]] =
    new QueryStringBindable[Option[LocalDate]] {

      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Option[LocalDate]]] = {
        try {
          Some(Right(params.get(key).map(a => new LocalDate(a.head))))
        } catch {
          case _: IllegalArgumentException | _: NumberFormatException =>
            Some(Left("Invalid local date format"))
        }
      }

      def unbind(key: String, value: Option[LocalDate]): String =
        value.map(key + "=" + _.toString).getOrElse("")

    }

  implicit def optionBindable[T: PathBindable]: PathBindable[Option[T]] = new PathBindable[Option[T]] {

    def bind(key: String, value: String): Either[String, Option[T]] =
      implicitly[PathBindable[T]]
        .bind(key, value)
        .fold(left => Left(left), right => Right(Some(right)))

    def unbind(key: String, value: Option[T]): String = value match {
      case Some(objectId: BSONObjectID) => objectId.stringify
      case _                            => value.map(_.toString).getOrElse("")
    }

  }

  implicit def optionJavascriptLiteral[T]: JavascriptLiteral[Option[T]] =
    (value: Option[T]) => value.map(_.toString).getOrElse("")

  implicit def bsonObjectIDPathBinder(implicit
      stringBinder: PathBindable[String]
  ): PathBindable[BSONObjectID] =
    new PathBindable[BSONObjectID] {

      override def bind(key: String, value: String): Either[String, BSONObjectID] = {
        for {
          id       <- stringBinder.bind(key, value)
          objectId <- BSONObjectID.parse(id).toOption.toRight("Invalid BSON object ID")
        } yield objectId
      }

      override def unbind(key: String, objectId: BSONObjectID): String = {
        stringBinder.unbind(key, objectId.stringify)
      }

    }

  implicit def bsonObjectIdQueryStringBinder(implicit
      stringBinder: QueryStringBindable[String]
  ): QueryStringBindable[BSONObjectID] =
    new QueryStringBindable[BSONObjectID] {

      override def bind(
          key   : String,
          params: Map[String, Seq[String]]
      ): Option[Either[String, BSONObjectID]] = {
        val id = stringBinder.bind(key, params)
        id match {
          case Some(Right(id)) if id != "" =>
            Some(BSONObjectID.parse(id).toOption.toRight("Invalid BSON object ID"))
          case _ => None
        }
      }

      override def unbind(key: String, objectID: BSONObjectID): String = {
        stringBinder.unbind(key, objectID.stringify)
      }

    }

  implicit def langQueryStringBinder(implicit
      stringBinder: QueryStringBindable[String]
  ): QueryStringBindable[Lang] =
    new QueryStringBindable[Lang] {

      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Lang]] = {
        val id = stringBinder.bind(key, params)
        id match {
          case Some(Right(lang)) if lang != "" => Some(Right(Lang(lang)))
          case _                               => None
        }
      }

      override def unbind(key: String, lang: Lang): String = {
        stringBinder.unbind(key, lang.code)
      }

    }

  implicit object LangPathBindable extends PathBindable[Lang] {

    def bind(key: String, value: String): Either[String, Lang] =
      try {
        Right(Lang(value))
      } catch {
        case _: Exception => Left("Cannot parse parameter '" + key + "' as Lang")
      }

    def unbind(key: String, value: Lang): String = value.code
  }

}

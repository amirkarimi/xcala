package xcala.play.postgres.utils

import play.api.i18n.Lang
import play.api.mvc.{PathBindable, QueryStringBindable}

import org.joda.time.{DateTime, LocalDate}

object Bindables {

  implicit def langQueryStringBinder(implicit stringBinder: QueryStringBindable[String]): QueryStringBindable[Lang] =
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

    def bind(key: String, value: String): Either[String, Lang] = try {
      Right(Lang(value))
    } catch {
      case _: Exception => Left("Cannot parse parameter '" + key + "' as Lang")
    }

    def unbind(key: String, value: Lang): String = key + "=" + value.code
  }

  implicit def jodaDateTimeBinder: QueryStringBindable[DateTime] = new QueryStringBindable[DateTime] {

    def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, DateTime]] = {
      try {
        Some(Right(new DateTime(params.get(key).get.head.toLong)))
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
        Some(Right(new LocalDate(params.get(key).get.head)))
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
          case _: IllegalArgumentException | _: NumberFormatException => Some(Left("Invalid local date format"))
        }
      }

      def unbind(key: String, value: Option[LocalDate]): String = value.map(key + "=" + _.toString).getOrElse("")
    }

}

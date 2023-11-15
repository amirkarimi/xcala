package xcala.play.extensions

import xcala.play.models.Range
import xcala.play.utils.KeywordExtractor

import play.api.data.Form
import play.api.data.FormError
import play.api.data.Forms
import play.api.data.Forms._
import play.api.data.Mapping
import play.api.data.format.Formatter
import play.api.i18n.Messages

import org.joda.time.DateTime
import org.joda.time.LocalDate

object FormHelper {

  private val persianEnglishNumbersMapping: Map[Char, Char] = Map(
    '۰' -> '0',
    '۱' -> '1',
    '۲' -> '2',
    '۳' -> '3',
    '۴' -> '4',
    '۵' -> '5',
    '۶' -> '6',
    '۷' -> '7',
    '۸' -> '8',
    '۹' -> '9'
  )

  def convertStringOfPersianNumbersToEnglish(a: String): String = {
    persianEnglishNumbersMapping.foldLeft(a) { case (str, (persian, english)) =>
      str.replace(persian, english)
    }
  }

  def convertPersianNumbersToEnglish(mapping: Mapping[String]): Mapping[String] = {
    mapping.transform[String](a => convertStringOfPersianNumbersToEnglish(a.trim), a => a)
  }

  def convertPersianNumbersToEnglishOptional(mapping: Mapping[Option[String]]): Mapping[Option[String]] = {
    mapping.transform[Option[String]](a => a.map(b => convertStringOfPersianNumbersToEnglish(b.trim)), a => a)
  }

  def jodaLocalDateFormatterWithYearRestriction(minYear: Int, maxYear: Int, pattern: String): Formatter[LocalDate] =
    new Formatter[LocalDate] {

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], LocalDate] =
        play.api.data.format.JodaFormats.jodaLocalDateFormat(pattern).bind(key, data) match {
          case Left(formErrors) => Left(formErrors)
          case Right(value)     =>
            val year = value.getYear()
            if (year < minYear || year > maxYear) {
              Left(Seq(FormError(key, "error.invalidFormat")))
            } else {
              Right(value)
            }
        }

      override def unbind(key: String, value: LocalDate): Map[String, String] = {
        val year = value.getYear()
        if (year < minYear || year > maxYear) {
          Map.empty[String, String]
        } else {
          play.api.data.format.JodaFormats.jodaLocalDateFormat(pattern).unbind(key, value)
        }
      }

    }

  def jodaDateTimeFormatterWithYearRestriction(minYear: Int, maxYear: Int, pattern: String): Formatter[DateTime] =
    new Formatter[DateTime] {

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], DateTime] =
        play.api.data.format.JodaFormats.jodaDateTimeFormat(pattern).bind(key, data) match {
          case Left(formErrors) =>
            Left(formErrors)
          case Right(value)     =>
            val year = value.getYear()
            if (year < minYear || year > maxYear) {
              Left(Seq(FormError(key, "error.invalidFormat")))
            } else {
              Right(value)
            }
        }

      override def unbind(key: String, value: DateTime): Map[String, String] = {
        val year = value.getYear()
        if (year < minYear || year > maxYear) {
          Map.empty[String, String]
        } else {
          play.api.data.format.JodaFormats.jodaDateTimeFormat(pattern).unbind(key, value)
        }
      }

    }

  def jodaLocalDateMappingWithYearRestriction(
      pattern: String = "yyyy-MM-dd",
      minYear: Int    = 1900,
      maxYear: Int    = 2500
  ): Mapping[org.joda.time.LocalDate] =
    Forms.of(
      jodaLocalDateFormatterWithYearRestriction(
        minYear = minYear,
        maxYear = maxYear,
        pattern = pattern
      )
    )

  def jodaDateTimeMappingWithYearRestriction(
      pattern: String,
      minYear: Int = 1900,
      maxYear: Int = 2500
  ): Mapping[org.joda.time.DateTime] =
    Forms.of(
      jodaDateTimeFormatterWithYearRestriction(
        minYear = minYear,
        maxYear = maxYear,
        pattern = pattern
      )
    )

  def trimmed(mapping: Mapping[String]): Mapping[String] = {
    mapping.transform[String]((a: String) => a.trim, (a: String) => a)
  }

  def removeSpecialCharacters(mapping: Mapping[String]): Mapping[String] = {
    mapping.transform[String](a => KeywordExtractor.removeSpecialCharacters(a.trim), a => a)
  }

  implicit class AdvancedForm[A](val form: Form[A]) extends AnyVal {

    def withErrorIf(hasError: Boolean, key: String, error: String, args: Any*): Form[A] = {
      if (hasError) {
        form.withError(key = key, message = error, args = args)
      } else {
        form
      }
    }

    def withErrors(formErrors: Seq[FormError]): Form[A] = {
      formErrors match {
        case head :: tail => new AdvancedForm(form.withError(head)).withErrors(tail)
        case _            => form

      }
    }

  }

  implicit class FixedLanguageForm[A](val form: Form[A]) extends AnyVal {

    /** When the language is Persian, converts incorrect used Arabic characters like "ي" and "ك" to correct Persian
      * ones.
      */
    def fixLanguageChars(implicit messages: Messages): Form[A] = {
      if (messages.lang.code == "fa") {
        val fixedData = form.data.map { case (key, value) =>
          (key, PersianUtils.convertToPersianChars(value))
        }

        form.discardingErrors.bind(fixedData)
      } else {
        form
      }
    }

  }

  lazy val dateRangeMapping: Mapping[Range[Option[LocalDate]]] = mapping(
    "from" -> optional(jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd")),
    "to"   -> optional(jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd"))
  )(Range.apply[Option[LocalDate]])(Range.unapply[Option[LocalDate]])

  lazy val dateRangeMappingNonOptional: Mapping[Range[LocalDate]] = mapping(
    "from" -> jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd"),
    "to"   -> jodaLocalDateMappingWithYearRestriction(pattern = "yyyy-MM-dd")
  )(Range.apply[LocalDate])(Range.unapply[LocalDate])

  lazy val dateTimeRangeMapping: Mapping[Range[Option[DateTime]]] = mapping(
    "from" -> optional(jodaDateTimeMappingWithYearRestriction(pattern = "yyyy-MM-dd HH:mm")),
    "to"   -> optional(jodaDateTimeMappingWithYearRestriction(pattern = "yyyy-MM-dd HH:mm"))
  )(Range.apply[Option[DateTime]])(Range.unapply[Option[DateTime]])

}

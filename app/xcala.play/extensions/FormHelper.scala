package xcala.play.extensions

import xcala.play.utils.KeywordExtractor

import play.api.data.FieldMapping
import play.api.data.Form
import play.api.data.FormError
import play.api.data.Forms
import play.api.data.Mapping
import play.api.data.format.Formatter
import play.api.i18n.Messages

import org.joda.time.LocalDate

object FormHelper {

  def jodaLocalDateFormatterWithYearRestriction(minYear: Int, maxYear: Int): Formatter[LocalDate] =
    new Formatter[LocalDate] {

      override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], LocalDate] =
        play.api.data.format.JodaFormats.jodaLocalDateFormat.bind(key, data) match {
          case Left(formErrors) => Left(formErrors)
          case Right(value) =>
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
          play.api.data.format.JodaFormats.jodaLocalDateFormat.unbind(key, value)
        }
      }

    }

  def jodaLocalDateMappingWithYearRestriction(minYear: Int = 1900, maxYear: Int = 2500): FieldMapping[LocalDate] =
    Forms.of(
      jodaLocalDateFormatterWithYearRestriction(
        minYear = minYear,
        maxYear = maxYear
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
        form.withError(key, error, args)
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

}

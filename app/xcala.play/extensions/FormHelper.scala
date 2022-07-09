package xcala.play.extensions

import play.api.data.Form
import play.api.data.FormError
import play.api.data.Mapping
import play.api.i18n.Lang
import play.api.i18n.Messages
import xcala.play.utils.KeywordExtractor

object FormHelper {

  def trimmed(mapping: Mapping[String]): Mapping[String] = {
    mapping.transform[String]((a: String) => a.trim, (a: String) => a)
  }

  def removeSpecialCharacters(mapping: Mapping[String]): Mapping[String] = {
    mapping.transform[String](a => KeywordExtractor.removeSpecialCharacters(a.trim), a => a)
  }

  implicit class AdvancedForm[A](val form: Form[A]) extends AnyVal {

    def withErrorIf(hasError: Boolean, key: String, error: String, args: Any*) = {
      if (hasError) {
        form.withError(key, error, args)
      } else {
        form
      }
    }

    def withErrors(formErrors: Seq[FormError]): Form[A] = {
      formErrors match {
        case Nil          => form
        case head :: tail => new AdvancedForm(form.withError(head)).withErrors(tail)
      }
    }

  }

  implicit class FixedLanguageForm[A](val form: Form[A]) extends AnyVal {

    /** When the language is Persian, converts incorrect used Arabic characters like "ي" and "ك" to correct Persian
      * ones.
      */
    def fixLanguageChars(implicit messages: Messages) = {
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

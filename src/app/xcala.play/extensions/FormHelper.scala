package xcala.play.extensions

import play.api.data.Form
import play.api.i18n.Lang

object FormHelper {
  implicit class AdvancedForm[A](val form: Form[A]) extends AnyVal {
    def withErrorIf(hasError: Boolean, key: String, error: String, args: Any*) = {
      if (hasError) {
        form.withError(key, error, args)
      } else {
        form
      }
    }
  }
  
  implicit class FixedLanguageForm[A](val form: Form[A]) extends AnyVal {
    /**
     *  When the language is Persian, converts incorrect used Arabic characters like "ي" and "ك" to correct Persian ones. 
     */
    def fixLanguageChars(implicit lang: Lang) = {
      if (lang.language == "fa") {
        val fixedData = form.data.map { case (key, value) =>
          (key,PersianUtils.convertToPersianChars(value))
        }

        form.bind(fixedData)
      } else {
        form
      }
    }
  }
}
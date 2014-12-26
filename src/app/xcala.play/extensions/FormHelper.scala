package xcala.play.extensions

import play.api.data.Form

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
}
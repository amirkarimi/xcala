package xcala.play.postgres.utils

import java.net.URI

object ReturnUrlValidationHelper {

  private def isRelativeUrl(url: String): Boolean = {
    val uri = new URI(url)
    !uri.isAbsolute
  }

  def withReturnUrlValidation[A](maybeReturnUrl: Option[String])(validatedReturnUrlToResult: Option[String] => A): A = {
    val validatedReturnUrl = maybeReturnUrl.filter { returnUrl =>
      isRelativeUrl(returnUrl) && !returnUrl.toLowerCase.contains("javascript")
    }
    validatedReturnUrlToResult(validatedReturnUrl)
  }

}

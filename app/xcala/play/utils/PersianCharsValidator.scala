package xcala.play.utils

object PersianCharsValidator {

  def isValidAlphaWithSpace(text: String): Boolean = {
    text.matches("^[\u0621-\u064A\uFB8A\u067E\u0686\u06AF\u06A9\u06CC\u0698\u200B\u200C ]+$")
  }

  def isValidAlphaWithSpaceAndNumbers(text: String): Boolean = {
    text.matches("^[\u0621-\u064A\uFB8A\u067E\u0686\u06AF\u06A9\u06CC\u0698\u200B\u200C\u06F0-\u06F90-9، ]+$")
  }

  def isNum(text: String): Boolean = {
    text.matches("^[\u06F0-\u06F9]+$")
  }

}

package xcala.play.utils

import scala.util.matching.Regex

object IbanValidator {
  val IranianIbanLength: Int   = 26
  val IbanPattern      : Regex = """([A-Z]{2})(\d{2})([A-Z0-9]{12,27})""".r

  def isValidIranianIban(iban: String): Boolean = {
    iban match {
      case IbanPattern(country, check, account) if country == "IR" && iban.length == 26 =>
        isValid(country = country, check = check, account = account)
      case _ =>
        false
    }
  }

  def isValid(country: String, check: String, account: String): Boolean = {
    val arranged        = account + country + check
    val arrangedInt     = convertToDigits(arranged)
    val arrangedDecimal = BigDecimal(arrangedInt)
    arrangedDecimal % 97 == BigDecimal(1)
  }

  def convertToDigits(in: String): String = {
    in.flatMap { ch =>
      if (ch.isLetter) {
        ((ch - 'A') + 10).toString
      } else {
        ch.toString
      }
    }
  }

}

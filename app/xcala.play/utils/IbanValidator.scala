package xcala.play.utils

object IbanValidator {
  val IranianIbanLength = 26
  val IbanPattern = """([A-Z]{2})(\d{2})([A-Z0-9]{12,27})""".r
  
  def isValidIranianIban(iban: String) = {
    iban match {
      case IbanPattern(country, check, account) if country == "IR" && iban.length == 26 =>
        isValid(country, check, account)
      case _ =>
        false
    }
  }
  
  def isValid(country: String, check: String, account: String) = {
    val arranged = account + country + check
    val arrangedInt = convertToDigits(arranged)
    val arrangedDecimal = BigDecimal(arrangedInt)
    arrangedDecimal % 97 == 1
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
package xcala.play.utils

object IranianNationalCodeHelper {

  def isValid(input: String): Boolean = {
    val pattern = """^(\d{10})$""".r
    input match {
      case pattern(_) =>
        val check = input.takeRight(1).toInt
        val sum = (0 to 8)
          .map(x => input.substring(x, x + 1).toInt * (10 - x))
          .sum % 11

        sum < 2 && check == sum || sum >= 2 && check + sum == 11
      case _ => false
    }
  }

}

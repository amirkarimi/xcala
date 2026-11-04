package xcala.play.utils

import scala.util.matching.Regex

object Patterns {

  val urlPattern: Regex =
    """[(http(s)?):\/\/(www\.)?a-zA-Z0-9:%._\+~#=]{2,256}\.[a-z]{1,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)""".r

  val accountUsernamePattern: Regex = ("(" + urlPattern.toString + ")?" + """[a-z0-9_\.]{3,50}""").r

}

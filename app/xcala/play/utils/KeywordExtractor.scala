package xcala.play.utils

import scala.collection.immutable.ArraySeq
import scala.util.matching.Regex

object KeywordExtractor {
  val BOM: String = "\uFEFF"

  private final val breaks: Regex =
    ("""؟|\?|؛|!|%|:|=|#|,|،|-|_|\(|\)|\[|\]|\"|\'|/|<br[ ]*>|[​-‍]|[<>]""" + s"|$BOM").r

  private final val ticketBreaks: Regex = ("""%|#|_|\(|\)|\[|\]|\"|\'|<br[ ]*>|[​-‍]|[<>]""" + s"|$BOM").r

  def getKeywords(text: String): Seq[String] = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ")
    ArraySeq.unsafeWrapArray(withSpace.split(" ").filter(_ != ""))
  }

  def removeSpecialCharacters(text: String): String = {
    val withSpace = breaks.replaceAllIn(text, " ")
    withSpace.trim.replaceAll(" +", " ")
  }

  def removeTicketSpecialCharacters(text: String): String = {
    val withSpace = ticketBreaks.replaceAllIn(text, " ")
    withSpace.trim.replaceAll(" +", " ")
  }

}

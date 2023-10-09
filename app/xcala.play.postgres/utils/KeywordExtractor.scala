package xcala.play.postgres.utils

import scala.collection.immutable.ArraySeq
import scala.util.matching.Regex

object KeywordExtractor {
  val BOM: String = "\uFEFF"

  private final val breaks: Regex =
    ("""؟|\?|؛|!|%|:|=|#|,|،|-|_|\(|\)|\[|\]|\"|\'|/|<br >|[​-‍]|<br/>|[<>]""" + s"|$BOM").r

  def getKeywords(text: String): Seq[String] = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ").replaceAll("<br  >", " ")
    ArraySeq.unsafeWrapArray(withSpace.split(" ").filter(_ != ""))
  }

  def removeSpecialCharacters(text: String): String = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ").replaceAll("<br  >", " ")
    withSpace.trim.replaceAll(" +", " ")
  }

}

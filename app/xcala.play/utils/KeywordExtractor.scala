package xcala.play.utils

import scala.collection.immutable.ArraySeq

object KeywordExtractor {
  val BOM = "\uFEFF"

  private final val breaks = ("""؟|\?|؛|!|%|:|=|#|,|،|-|_|\(|\)|\[|\]|\"|\'|/|<br >|[​-‍]|<br/>|[<>]""" + s"|$BOM").r

  def getKeywords(text: String): Seq[String] = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ").replaceAll("<br  >", " ")
    ArraySeq.unsafeWrapArray(withSpace.split(" ").filter(_ != ""))
  }

  def removeSpecialCharacters(text: String): String = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ").replaceAll("<br  >", " ")
    withSpace.trim.replaceAll(" +", " ")
  }

}

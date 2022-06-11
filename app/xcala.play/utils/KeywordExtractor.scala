package xcala.play.utils

object KeywordExtractor {
  private final val breaks = """؟|\?|؛|!|%|:|=|#|,|،|-|_|\(|\)|\[|\]|\"|\'|/|<br >|[\u200B-\u200D\uFEFF]|<br/>|[<>]""".r

  def getKeywords(text: String): Seq[String] = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ").replaceAll("<br  >", " ")
    withSpace.split(" ").filter(_ != "")
  }

  def removeSpecialCharacters(text: String): String = {
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ").replaceAll("<br  >", " ")
    withSpace.trim.replaceAll(" +", " ")
  }

}
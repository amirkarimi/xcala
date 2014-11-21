package xcala.play.utils

object KeywordExtractor {
  private final val breaks = """,|،|\(|\)|\[|\]|\"|\'|[\u200B-\u200D\uFEFF]""".r
  
  def getKeywords(text: String): Seq[String] = {
    
    val withSpace = breaks.replaceAllIn(text.toLowerCase(), " ")
    val splitted = withSpace.split(" ").filter(_ != "")
    
    splitted
  }
}
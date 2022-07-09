package views.html.xcala.play

import play.twirl.api.Html

object briefHtml {

  def apply(content: String, wordCount: Int = 0, addEllipsis: Boolean = true) = {
    // Remove HTML tags
    val original = content.replaceAll("(?i)<[^>]*>", " ").replaceAll("\\s+", " ").trim()

    // Truncate the string
    if (wordCount == 0) {
      original
    } else {
      val truncated = original.split(" ").take(wordCount).mkString(" ")
      truncated + (if (truncated.length != original.length && addEllipsis) { " ..." }
                   else { "" })
    }
  }

}

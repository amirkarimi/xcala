package xcala.play.utils

object XmlUnescape {

  def unescape(text: String): String = {
    def recUnescape(textList: List[Char], acc: String, escapeFlag: Boolean): String = {
      textList match {
        case Nil                                            => acc
        case '&' :: tail                                    => recUnescape(tail, acc, true)
        case ';' :: tail if escapeFlag                      => recUnescape(tail, acc, false)
        case 'a' :: 'm' :: 'p' :: tail if escapeFlag        => recUnescape(tail, acc + "&", true)
        case 'q' :: 'u' :: 'o' :: 't' :: tail if escapeFlag => recUnescape(tail, acc + "\"", true)
        case 'l' :: 't' :: tail if escapeFlag               => recUnescape(tail, acc + "<", true)
        case 'g' :: 't' :: tail if escapeFlag               => recUnescape(tail, acc + ">", true)
        case x :: tail                                      => recUnescape(tail, acc + x, true)
        case _                                              => acc
      }
    }
    recUnescape(text.toList, "", false)
  }

}

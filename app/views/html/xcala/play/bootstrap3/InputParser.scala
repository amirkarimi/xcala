package views.html.xcala.play.bootstrap3

object InputParser {

  def addClass(input: String, className: String): String = {
    val maybeInfo = getInputInfo(input)
    maybeInfo
      .map { info =>
        if (isValidInput(info.args)) {
          val args = addClassToArgs(info.args, className)
          s"<${info.tag}$args>${info.body}"
        } else {
          input
        }
      }
      .getOrElse {
        input
      }
  }

  private def getInputInfo(input: String): Option[InputInfo] = {
    "(?s)<(input|textarea|select)(.*?)>(.*)".r.findFirstMatchIn(input).map { a =>
      InputInfo(tag = a.group(1), args = a.group(2), body = a.group(3))
    }
  }

  private def isValidInput(args: String): Boolean = {
    val found = """type(\s*?)?=(\s*?)?(["']?)(checkbox|radio)(["']?)""".r.findFirstMatchIn(args)
    found.isEmpty
  }

  private def addClassToArgs(args: String, className: String): String = {
    if (args.matches("""(.*)class=["'](.*)""")) {
      args.replaceFirst("(.*)(class=[\"'])(.*)", "$1$2" + className + " $3")
    } else {
      args + " class=\"" + className + "\""
    }
  }

  final case class InputInfo(tag: String, args: String, body: String)
}

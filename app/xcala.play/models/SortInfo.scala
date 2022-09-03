package xcala.play.models

case class SortInfo(field: String, direction: Int = 1) {
  def toggleDirection: SortInfo = copy(direction = direction * -1)

  override def toString: String = {
    val prefix = if (direction == -1) "-" else ""
    prefix + field
  }

}

object SortInfo {

  def fromExpression(expression: String): SortInfo = {
    apply(removeDirectionMark(expression), getSortDirection(expression))
  }

  def removeDirectionMark(s: String): String = if (s.startsWith("-")) s.substring(1) else s
  def getSortDirection(s: String): Int       = if (s.startsWith("-")) -1 else 1
}

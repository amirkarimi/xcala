package xcala.play.models

case class SortInfo(field: String, direction: Int = 1) {
  def toggleDirection = copy(direction = direction * -1)
  
  override def toString = {
    val prefix = if (direction == -1) "-" else ""
    prefix + field
  }
}

object SortInfo {
  def fromExpression(expression: String) = {
    apply(removeDirectionMark(expression), getSortDirection(expression))          
  }
  
  def removeDirectionMark(s: String) = if (s.startsWith("-")) s.substring(1) else s
  def getSortDirection(s: String) = if (s.startsWith("-")) -1 else 1
}

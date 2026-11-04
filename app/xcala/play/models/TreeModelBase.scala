package xcala.play.models

trait TreeModelBase[Id, A <: TreeModelBase[Id, _]] {
  def id          : Option[Id]
  def parentId    : Option[Id]
  def generalTitle: String
  def children    : List[A]
}

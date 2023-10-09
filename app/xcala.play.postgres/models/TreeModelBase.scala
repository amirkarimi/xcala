package xcala.play.postgres.models

trait TreeModelBase[A <: TreeModelBase[_]] {
  def id: Option[Long]
  def parentId: Option[Long]
  def generalTitle: String
  def children: List[A]
}

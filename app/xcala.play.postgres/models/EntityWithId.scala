package xcala.play.postgres.models

trait EntityWithId {
  def id: Option[Long]
}

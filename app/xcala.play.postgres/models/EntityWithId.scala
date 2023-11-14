package xcala.play.postgres.models

trait EntityWithId[K] {
  def id: Option[K]
}

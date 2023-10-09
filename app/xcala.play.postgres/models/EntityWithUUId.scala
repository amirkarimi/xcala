package xcala.play.postgres.models

import java.util.UUID

trait EntityWithUUId {
  def id: Option[UUID]
}

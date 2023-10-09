package xcala.play.postgres.models

import java.util.UUID

import slick.lifted.Rep

trait TableWithUUId {
  def id: Rep[UUID]
}

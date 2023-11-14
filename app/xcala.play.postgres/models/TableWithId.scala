package xcala.play.postgres.models

import slick.lifted.Rep

trait TableWithId[Id] {
  def id: Rep[Id]
}

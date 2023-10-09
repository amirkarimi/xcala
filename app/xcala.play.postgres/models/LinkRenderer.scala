package xcala.play.postgres.models

import java.util.UUID

trait LinkRenderer {
  def getLink(id: UUID): String
}

package xcala.play.postgres.entities

import xcala.play.postgres.models.TableWithId

trait TableDefinition[A] {
  val profile   : xcala.play.postgres.utils.MyPostgresProfile
  import profile.api._
  type TableDef <: Table[A] with TableWithId
  val tableQuery: TableQuery[TableDef]
}

package xcala.play.postgres.entities

import xcala.play.postgres.models.TableWithId

trait TableDefinition[Id, A] {
  val profile: xcala.play.postgres.utils.MyPostgresProfile
  import profile.api._
  type TableDef <: Table[A] with TableWithId[Id]
  val tableQuery: TableQuery[TableDef]
}

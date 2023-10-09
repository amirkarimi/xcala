package xcala.play.postgres.entities

import xcala.play.postgres.models.TableWithUUId

trait TableDefinitionWithUUID[A] {
  val profile: xcala.play.postgres.utils.MyPostgresProfile
  import profile.api._
  type TableDef <: Table[A] with TableWithUUId
  def tableQuery: TableQuery[TableDef]
}

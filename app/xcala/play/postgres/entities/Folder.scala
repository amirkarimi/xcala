package xcala.play.postgres.entities

import xcala.play.postgres.entities.TableDefinition
import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.models.TableWithId

import javax.inject._

import slick.lifted.ForeignKeyQuery

final case class Folder(
    id      : Option[Long] = None,
    name    : String,
    parentId: Option[Long]
) extends EntityWithId[Long]

@Singleton
class FolderTable @Inject() (val profile: xcala.play.postgres.utils.MyPostgresProfile)
    extends TableDefinition[Long, Folder] {
  import profile.api._
  val tableQuery: TableQuery[TableDef] = TableQuery[TableDef]

  class TableDef(tag: Tag) extends Table[Folder](tag, "Folders") with TableWithId[Long] {
    def id      : Rep[Long]         = column[Long]("id", O.AutoInc, O.PrimaryKey)
    def name    : Rep[String]       = column[String]("name")
    def parentId: Rep[Option[Long]] = column[Option[Long]]("parentId")
    def * = (id.?, name, parentId).shaped.<>((Folder.apply _).tupled, Folder.unapply)

    def parent: ForeignKeyQuery[TableDef, Folder] =
      foreignKey(name = "fk_Folders_parentId", sourceColumns = parentId, targetTableQuery = tableQuery)(
        targetColumns = _.id.?,
        onDelete      = ForeignKeyAction.Cascade
      )

  }

}

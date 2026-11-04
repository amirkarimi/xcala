package xcala.play.postgres.entities

import xcala.play.postgres.entities.TableDefinition
import xcala.play.postgres.models._

import java.util.UUID
import javax.inject._

import com.github.tototoshi.slick.PostgresJodaSupport._
import org.joda.time.DateTime
import slick.lifted.ForeignKeyQuery
import slick.lifted.ProvenShape.proveShapeOf

@Singleton
class FileTable @Inject() (
    val profile    : xcala.play.postgres.utils.MyPostgresProfile,
    val folderTable: FolderTable
) extends TableDefinition[UUID, FileInfo] {
  import profile.api._
  val tableQuery: TableQuery[TableDef] = TableQuery[TableDef]

  class TableDef(tag: Tag) extends Table[FileInfo](tag, "Files") with TableWithId[UUID] {
    def id         : Rep[UUID]         = column[UUID]("id", O.PrimaryKey, O.SqlType("UUID"), O.Default(UUID.randomUUID))
    def name       : Rep[String]       = column[String]("name")
    def fileName   : Rep[String]       = column[String]("fileName")
    def extension  : Rep[String]       = column[String]("extension")
    def contentType: Rep[String]       = column[String]("contentType")
    def length     : Rep[Long]         = column[Long]("length")
    def createTime : Rep[DateTime]     = column[DateTime]("createTime")
    def folderId   : Rep[Option[Long]] = column[Option[Long]]("folderId")
    def isHidden   : Rep[Boolean]      = column[Boolean]("isHidden")

    def * = (
      id.?,
      name,
      fileName,
      extension,
      contentType,
      length,
      createTime,
      folderId,
      isHidden
    ).shaped.<>((FileInfo.apply _).tupled, FileInfo.unapply)

    def folder: ForeignKeyQuery[folderTable.TableDef, Folder] =
      foreignKey(
        name             = "fk_Files_folderId",
        sourceColumns    = folderId,
        targetTableQuery = folderTable.tableQuery
      )(
        targetColumns = _.id.?,
        onDelete      = ForeignKeyAction.Cascade
      )

  }

}

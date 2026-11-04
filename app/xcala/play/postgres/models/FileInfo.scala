package xcala.play.postgres.models

import java.util.UUID

import org.joda.time.DateTime

final case class FileInfo(
    id         : Option[UUID] = None,
    name       : String,
    fileName   : String,
    extension  : String,
    contentType: String,
    length     : Long,
    createTime : DateTime,
    folderId   : Option[Long],
    isHidden   : Boolean
) extends EntityWithId[UUID] {
  def isImage: Boolean = contentType.startsWith("image/")
}

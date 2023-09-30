package xcala.play.models

import org.joda.time.DateTime
import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.bson.Macros.Annotations.Key

final case class FileInfo(
    @Key("_id") id: Option[BSONObjectID] = None,
    name          : String,
    extension     : String,
    contentType   : String,
    length        : Long,
    createTime    : DateTime,
    folderId      : Option[BSONObjectID],
    isHidden      : Boolean
) {
  def isImage: Boolean = contentType.startsWith("image/")
}

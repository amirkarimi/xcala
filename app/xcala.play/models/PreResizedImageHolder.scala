package xcala.play.models

import reactivemongo.api.bson.BSONObjectID

trait PreResizedImageHolder {
  def maybeImageFileId: Option[BSONObjectID]
}

package xcala.play.models

import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.bson.Macros.Annotations.Key
import org.joda.time.DateTime

case class IndexedItem(
    @Key("_id") id: Option[BSONObjectID],
    itemType: String,
    itemId: BSONObjectID,
    lang: String,
    title: String,
    content: String,
    updateTime: DateTime
) extends WithLang

case class IndexedItemCriteria(lang: Option[String], query: Option[String])

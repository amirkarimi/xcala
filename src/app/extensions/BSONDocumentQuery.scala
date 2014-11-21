package xcala.play.extensions

import org.joda.time.DateTime
import reactivemongo.bson._
import xcala.play.models._

object BSONDocumentQuery {
  implicit class BSONDocumentQueryExtender(val doc: BSONDocument) extends AnyVal {
    def filterByDateRange(field: String, range: Range[Option[DateTime]]) = {
      doc ++
        BSONDocument(field -> range.from.map(date => BSONDocument("$gte" -> BSONDateTime(date.getMillis)))) ++
        BSONDocument(field -> range.to.map(date => BSONDocument("$lte" -> BSONDateTime(date.getMillis))))
    }

    def filterByRange[A](field: String, range: Range[Option[A]])(implicit writer: BSONWriter[A, _ <: BSONValue]) = {
      doc ++
        BSONDocument(field -> range.from.map(value => BSONDocument("$gte" -> value))) ++
        BSONDocument(field -> range.to.map(value => BSONDocument("$lte" -> value)))
    }

    def filterByQueryWithType(field: String, queryWithType: QueryWithType) = queryWithType.query match {
      case None => doc
      case Some(query) if query.trim() != "" =>
        queryWithType.searchType match {
          case SearchType.Exact =>
            doc ++ BSONDocument(field -> query)
          case SearchType.Contains =>
            doc ++ BSONDocument("keywords." + field -> BSONDocument("$all" -> getQueryParts(query)))
        }
    }

    private def getQueryParts(query: String) = {
      query.toLowerCase.split(Array(' ', 0x200B.toChar, 0x200C.toChar, 0x200D.toChar, 0xFEFF.toChar)).toList.filter(_.length != 0)
    }
  }
}

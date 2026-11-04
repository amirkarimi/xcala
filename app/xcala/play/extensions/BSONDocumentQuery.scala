package xcala.play.extensions

import xcala.play.models._

import org.joda.time.DateTime
import reactivemongo.api.bson._

object BSONDocumentQuery {

  implicit class BSONDocumentQueryExtender(val doc: BSONDocument) extends AnyVal {

    def filterByDateRange(field: String, range: Range[Option[DateTime]]): BSONDocument = {
      doc ++
        BSONDocument(field -> range.from.map(date => BSONDocument("$gte" -> BSONDateTime(date.getMillis)))) ++
        BSONDocument(field ->
          range.to.map(date => BSONDocument("$lt" -> BSONDateTime(date.plusDays(1).getMillis))))
    }

    def filterByRange[A](field: String, range: Range[Option[A]])(implicit
        writer: BSONWriter[A]
    ): BSONDocument = {
      doc ++ BSONDocument(field -> range.from.map(value => BSONDocument("$gte" -> value))) ++
        BSONDocument(field -> range.to.map(value => BSONDocument("$lte" -> value)))
    }

    def filterByQueryWithType(field: String, queryWithType: QueryWithType): BSONDocument =
      queryWithType.query match {
        case None => doc
        case Some(query) if query.trim() != "" =>
          queryWithType.searchType match {
            case SearchType.Exact =>
              doc ++ BSONDocument(field -> query)
            case SearchType.Contains =>
              doc ++ BSONDocument("keywords." + field -> BSONDocument("$all" -> getQueryParts(query)))
            case _ => ???
          }
        case _ => ???
      }

    private def getQueryParts(query: String): List[String] = {
      query.toLowerCase
        .split(Array(' ', 0x200b.toChar, 0x200c.toChar, 0x200d.toChar, 0xfeff.toChar))
        .toList
        .filter(_.nonEmpty)
    }

  }

}

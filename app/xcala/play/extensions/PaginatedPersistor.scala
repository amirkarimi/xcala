package xcala.play.extensions

import xcala.play.models._

import play.api.mvc._

import io.lemonlabs.uri.typesafe.dsl._

object PaginatedPersistor {

  implicit class PaginatedPersistorCall(val call: Call) extends AnyVal {

    def paginatedUrl[A](paginated: Paginated[A], keyName: String = "paginatedParams"): Call = {
      call.copy(url = call.toString.addParam(keyName -> paginated.toQueryString).toString)
    }

    def copyPaginatedUrl(keyName: String = "paginatedParams")(implicit request: RequestHeader): Call = {
      val url = request.queryString.get(keyName) match {
        case None         => call.toString
        case Some(values) => call.toString.addParam(keyName -> values.mkString).toString
      }
      call.copy(url = url)
    }

    def withPaginatedQueryStringUrl(
        keyName       : String      = "paginatedParams",
        additionalKeys: Set[String] = Set.empty[String]
    )(implicit
        request: RequestHeader
    ): Call = {
      val keys: Set[String] = additionalKeys + keyName

      val url = keys.foldLeft[String](call.toString) { case (prevUrl, nextKey) =>
        request.queryString.get(nextKey) match {
          case Some(values) if values.exists(_.nonEmpty) =>
            nextKey match {
              case "paginatedParams" =>
                attachQueryString(prevUrl, values.mkString)

              case _: String =>
                val stringValue = values.head
                attachQueryString(prevUrl, s"$nextKey=$stringValue")

            }
          case _ => prevUrl
        }

      }

      call.copy(url = url)
    }

    def attachQueryString(source: String, queryString: String): String =
      if (!source.contains(queryString)) {
        val separator = if (source.contains("?")) "&" else "?"
        source + separator + queryString
      } else {
        source
      }

  }

}

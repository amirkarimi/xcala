package xcala.play.extensions

import play.api.mvc._
import xcala.play.models._
import com.netaporter.uri.dsl._
import com.netaporter.uri.QueryString

object PaginatedPersistor {
  implicit class PaginatedPersistorCall(val call: Call) extends AnyVal {
    def paginatedUrl[A](paginated: Paginated[A], keyName: String = "paginatedParams") = {
      call.copy(url = call.toString ? (keyName -> paginated.toQueryString))
    }
    
    def withPaginatedQueryStringUrl(keyName: String = "paginatedParams")(implicit request: RequestHeader) = {
      val url = request.queryString.get(keyName) match {
        case None => call.toString
        case Some(values) => attachQueryString(call.toString, values.mkString)
      }
      call.copy(url = url)
    }
    
    private def attachQueryString(source: String, queryString: String) = {
      val separator = if (source.contains("?")) "&" else "?"
      source + separator + queryString
    }
    
  }

}
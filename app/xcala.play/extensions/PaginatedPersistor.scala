package xcala.play.extensions

import play.api.mvc._
import xcala.play.models._
import io.lemonlabs.uri.dsl._

object PaginatedPersistor {
  implicit class PaginatedPersistorCall(val call: Call) extends AnyVal {
    def paginatedUrl[A](paginated: Paginated[A], keyName: String = "paginatedParams") = {
      call.copy(url = call.toString ? (keyName -> paginated.toQueryString))
    }
    
    def copyPaginatedUrl(keyName: String = "paginatedParams")(implicit request: RequestHeader) = {
      val url = request.queryString.get(keyName) match {
        case None => call.toString
        case Some(values) => (call.toString ? (keyName -> values.mkString)).toString
      }
      call.copy(url = url)
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
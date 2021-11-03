package xcala.play.models

import play.api.mvc.Headers

class WrappedHeaders(val data: Seq[(String, String)]) extends Headers(data)

object WrappedHeaders {
  def apply(headers: Headers, items: Seq[(String, String)]) = {
    val data = headers.headers ++ items
    new WrappedHeaders(data)
  }
}
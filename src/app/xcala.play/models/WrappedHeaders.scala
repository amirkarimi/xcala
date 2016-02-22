package xcala.play.models

import play.api.mvc.Headers

class WrappedHeaders(val data: Seq[(String, Seq[String])]) extends Headers

object WrappedHeaders {
  def apply(headers: Headers, items: (String, Seq[String])*) = {
    val data = (headers.toMap ++ items).toSeq
    new WrappedHeaders(data)
  }
}
package xcala.play.utils

import play.api.http.HeaderNames._

object HttpHeaderHelper {

  def contentDispositionHeaderRaw(fileName: String, extension: String = "xlsx"): (String, String) =
    CONTENT_DISPOSITION -> s"attachment; filename=$fileName.$extension"

  def contentDispositionHeaderEncoded(fileName: String, extension: String = "xlsx"): (String, String) =
    CONTENT_DISPOSITION ->
      (s"""attachment; filename="${java.net.URLEncoder
          .encode(s"$fileName.$extension", "UTF-8")
          .replace("+", "%20")}"; filename*=UTF-8''""" +
      java.net.URLEncoder
        .encode(s"$fileName.$extension", "UTF-8")
        .replace("+", "%20"))

}

package xcala.play.extensions

import play.api.mvc._
import play.mvc.Http.HeaderNames
import play.api.i18n.Lang
import xcala.play.models.WrappedHeaders

object HttpHelper {

  implicit class XcalaRequest[A](val request: Request[A]) extends AnyVal {
    def withLang(lang: Lang): Request[A] = {
      val requestHeaderWithLang = request.withHeaders(newHeaders = WrappedHeaders(request.headers, Seq(HeaderNames.ACCEPT_LANGUAGE -> lang.toString)))
      Request(requestHeaderWithLang, request.body)    
    }
  }

}
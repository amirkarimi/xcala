package xcala.play.extensions

import xcala.play.models.WrappedHeaders

import play.api.i18n.Lang
import play.api.mvc._
import play.mvc.Http.HeaderNames

object HttpHelper {

  implicit class XcalaRequest[A](val request: Request[A]) extends AnyVal {

    def withLang(lang: Lang): Request[A] = {
      val requestHeaderWithLang = request.withHeaders(newHeaders =
        WrappedHeaders(request.headers, Seq(HeaderNames.ACCEPT_LANGUAGE -> lang.toString))
      )
      Request(requestHeaderWithLang, request.body)
    }

  }

}

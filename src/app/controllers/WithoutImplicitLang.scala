package xcala.play.controllers

import play.api.mvc.Controller

trait WithoutImplicitLang extends Controller {
  override def request2lang(implicit request: play.api.mvc.RequestHeader) = super.request2lang
}

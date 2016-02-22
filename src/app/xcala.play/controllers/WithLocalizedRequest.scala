package xcala.play.controllers

import play.api.mvc._
import xcala.play.models.LocalizedRequest

trait WithLocalizedRequest extends Controller {
  
  override implicit def request2lang(implicit request: RequestHeader) = request match {
    case r: LocalizedRequest[_] => r.lang
    case _ => super.request2lang
  }
  
}
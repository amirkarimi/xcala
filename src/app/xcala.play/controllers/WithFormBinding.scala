package xcala.play.controllers

import play.api.mvc.Request

import scala.concurrent.Future
import play.api.data.Form

trait WithFormBinding {
  type RequestType <: Request[_]
  
  protected def bindForm[B](form: Form[B])(implicit request: RequestType): Future[Form[B]] = {
    Future.successful(form.bindFromRequest)
  }

}
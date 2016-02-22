package xcala.play.controllers

import play.api.mvc.Request

import scala.concurrent.Future
import play.api.data.Form

trait WithFormBinding {
  type RequestType[A] <: Request[A]
  
  protected def bindForm[B](form: Form[B])(implicit request: RequestType[_]): Future[Form[B]] = {
    Future.successful(form.bindFromRequest)
  }

}
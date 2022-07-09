package xcala.play.controllers

import play.api.mvc.Request

import scala.concurrent.Future
import play.api.data.Form
import play.api.data.FormBinding

trait WithFormBinding {
  type RequestType[A] <: Request[A]

  protected def bindForm[B](
      form: Form[B]
  )(implicit request: RequestType[_], formBinding: FormBinding): Future[Form[B]] = {
    Future.successful(form.bindFromRequest)
  }

}

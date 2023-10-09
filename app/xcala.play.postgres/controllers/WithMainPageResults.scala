package xcala.play.postgres.controllers

import play.api.i18n.Messages
import play.api.mvc._

trait WithMainPageResults extends Results {

  def mainPageRoute(implicit request: RequestHeader): Call

  protected def successfulResult(message: String)(implicit request: RequestHeader, messages: Messages): Result = {
    Redirect(mainPageRoute).flashing("success" -> messages(message))
  }

  protected def failedResult(message: String)(implicit request: RequestHeader, messages: Messages): Result = {
    Redirect(mainPageRoute).flashing("error" -> messages(message))
  }

}

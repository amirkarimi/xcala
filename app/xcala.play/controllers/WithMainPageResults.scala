package xcala.play.controllers

import play.api.i18n.Messages
import play.api.mvc._

trait WithMainPageResults extends Results {

  def mainPageRoute(implicit requestHeader: RequestHeader): Call

  protected def successfulResult(message: String)(implicit messages: Messages, requestHeader: RequestHeader): Result = {
    Redirect(mainPageRoute).flashing("success" -> messages(message))
  }

  protected def failedResult(message: String)(implicit messages: Messages, requestHeader: RequestHeader): Result = {
    Redirect(mainPageRoute).flashing("error" -> messages(message))
  }

}

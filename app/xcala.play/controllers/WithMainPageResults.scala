package xcala.play.controllers

import play.api.mvc._
import play.api.i18n.Messages

trait WithMainPageResults extends Results {

  def mainPageRoute(implicit requestHeader: RequestHeader): Call

  protected def successfulResult(message: String)(implicit messages: Messages, requestHeader: RequestHeader): Result = {
    Redirect(mainPageRoute).flashing("success" -> message)
  }

  protected def failedResult(message: String)(implicit messages: Messages, requestHeader: RequestHeader): Result = {
    Redirect(mainPageRoute).flashing("error" -> message)
  }

}

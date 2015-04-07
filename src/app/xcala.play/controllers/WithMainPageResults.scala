package xcala.play.controllers

import play.api.mvc._
import play.api.i18n.Lang

trait WithMainPageResults extends Results {

  def mainPageRoute: Call

  protected def successfulResult(message: String)(implicit lang: Lang): Result = {
    Redirect(mainPageRoute).flashing("success" -> message)
  }

  protected def failedResult(message: String)(implicit lang: Lang): Result = {
    Redirect(mainPageRoute).flashing("error" -> message)
  }
}
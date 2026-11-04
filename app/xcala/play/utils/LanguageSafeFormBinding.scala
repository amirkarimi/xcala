package xcala.play.utils

import xcala.play.extensions.FormHelper._

import play.api.data.Form
import play.api.i18n.Messages
import play.api.mvc.Request

object LanguageSafeFormBinding {

  def bindForm[B](
      form: Form[B]
  )(implicit request: Request[_], formBinding: play.api.data.FormBinding, messages: Messages): Form[B] = {
    form.bindFromRequest().fixLanguageChars
  }

  def bindForm[B](
      form: Form[B],
      data: Map[String, Seq[String]]
  )(implicit messages: Messages): Form[B] = {
    form.bindFromRequest(data).fixLanguageChars
  }

}

package xcala.play.controllers

import play.api.i18n.Lang
import play.api.mvc.Action

object WithLang {
  def apply[A](lang: Lang)(block: Lang => Action[A]): Action[A] = block(lang)
}

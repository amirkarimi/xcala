package xcala.play.extensions

import xcala.play.models.MultilangText
import play.api.i18n.Lang

object MultilangTextHelper {
  implicit class MultilangTextViewer(val value: List[MultilangText]) extends AnyVal {
    def getLangValue(implicit lang: Lang) = {
      value.filter(_.lang == lang.code).map(_.value).headOption.getOrElse("")
    }
  }
}
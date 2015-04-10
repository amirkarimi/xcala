package xcala.play.extensions

import xcala.play.models.MultilangText
import play.api.i18n.Lang

object MultilangTextHelper {
  implicit class MultilangTextViewer(val value: List[MultilangText]) extends AnyVal {
    /**
     * Returns the value of specified language if available, otherwise returns empty string.
     */
    def getLangValue(implicit lang: Lang) = {
      getLangValueOpt.getOrElse("")
    }
    
    /**
     * Returns the value of specified language if available, otherwise returns the first existing language.
     */
    def getLangValueOrExisting(implicit lang: Lang) = {
      getLangValueOpt.getOrElse(value.map(_.value).headOption.getOrElse(""))
    }
    
    /**
     * Returns the value of specified language if available.
     */
    def getLangValueOpt(implicit lang: Lang) = {
      value.filter(_.lang == lang.code).map(_.value).headOption
    }
  }
}
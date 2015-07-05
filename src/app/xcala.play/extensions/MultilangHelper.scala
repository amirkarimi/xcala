package xcala.play.extensions

import xcala.play.models.{MultilangModel}
import play.api.i18n.Lang

object MultilangHelper {
  implicit class MultilangModelViewer[A](val value: List[MultilangModel[A]]) extends AnyVal {

    /**
     * Returns the value of specified language if available, otherwise returns the first existing language.
     */
    def getLangValueOrExisting(implicit lang: Lang): Option[A] = {
      getLangValue.orElse(value.map(_.value).headOption)
    }
    
    /**
     * Returns the value of specified language if available.
     */
    def getLangValue(implicit lang: Lang): Option[A] = {
      value.filter(_.lang == lang.code).map(_.value).headOption
    }
  }
}
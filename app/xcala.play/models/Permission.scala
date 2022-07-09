package xcala.play.models

import play.api.i18n.Lang
import play.api.i18n.Messages

case class Permission(id: String) {

  override def equals(o: Any) = o match {
    case that: Permission => that.id.equalsIgnoreCase(this.id)
    case _                => false
  }

  def title(implicit messages: Messages) = Messages(id)
}

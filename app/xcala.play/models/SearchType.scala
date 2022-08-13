package xcala.play.models

import play.api.i18n.Messages

object SearchType {
  type Type = String

  val Contains = "contains"
  val Exact    = "exact"

  def all(implicit messages: Messages): Seq[(String, String)] = Seq(
    Contains,
    Exact
  ).map(a => (a, Messages("searchType." + a)))

}

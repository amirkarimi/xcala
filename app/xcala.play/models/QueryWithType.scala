package xcala.play.models

import play.api.i18n.Lang
import play.api.i18n.Messages

case class QueryWithType(query: Option[String], searchType: SearchType.Type)

object SearchType {
  type Type = String
  
  val Contains = "contains"
  val Exact = "exact"  
    
  def all(implicit messages: Messages) = Seq(
    Contains,
    Exact
  ).map(a => (a, Messages("searchType." + a)))
}

package xcala.play.models

import play.api.i18n.Messages

trait Options {
  type Type
  
  val MessagePrefix: String
  
  def all: Seq[Type]
  
  def getTitle(item: Type)(implicit messages: Messages) = messages(MessagePrefix + all.find(_ == item).getOrElse(""))

  def allWithTitle(implicit messages: Messages) = all.map(toOption)
  
  protected def toOption(item: Type)(implicit messages: Messages) = (item, messages(MessagePrefix + item))
}
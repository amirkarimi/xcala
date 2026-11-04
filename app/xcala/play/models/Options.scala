package xcala.play.models

import play.api.i18n.Messages

trait Options {
  type Type

  val MessagePrefix: String

  def all: Seq[Type]

  def getTitle(item: Type)(implicit messages: Messages): String = messages(
    MessagePrefix + all.find(_ == item).getOrElse("")
  )

  def allWithTitle(implicit messages: Messages): Seq[(String, String)] = all.map(toOption)

  protected def toOption(item: Type)(implicit messages: Messages): (String, String) =
    (item.toString, messages(MessagePrefix + item))

}

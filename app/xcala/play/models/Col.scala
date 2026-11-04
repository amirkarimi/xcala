package xcala.play.models

import play.api.i18n.Messages

final class Col[A](
    val maybeFieldValueMapper: A => Option[String],
    val name                 : String,
    val sortExpression       : Any,
    val cssClass             : A => Option[String],
    val headerCssClass       : Option[String],
    val addIdToSort          : Boolean
) {
  lazy val fieldMapper: A => String = maybeFieldValueMapper.andThen(_.mkString)
}

object Col {

  def fromOptionalField[A](
      name                 : String,
      maybeFieldValueMapper: A => Option[String],
      sortExpression       : Any                 = "",
      cssClass             : A => Option[String] = (_: A) => None,
      headerCssClass       : Option[String]      = None,
      addIdToSort          : Boolean             = false
  ): Col[A] = new Col(
    name                  = name,
    maybeFieldValueMapper = maybeFieldValueMapper,
    sortExpression        = sortExpression,
    cssClass              = cssClass,
    headerCssClass        = headerCssClass,
    addIdToSort           = addIdToSort
  )

  def apply[A](
      name          : String,
      fieldMapper   : A => String,
      sortExpression: Any                 = "",
      cssClass      : A => Option[String] = (_: A) => None,
      headerCssClass: Option[String]      = None,
      addIdToSort   : Boolean             = false
  ): Col[A] =
    new Col[A](
      maybeFieldValueMapper = fieldMapper.andThen(Some(_)),
      name                  = name: String,
      sortExpression        = sortExpression: Any,
      cssClass              = cssClass: A => Option[String],
      headerCssClass        = headerCssClass: Option[String],
      addIdToSort           = addIdToSort: Boolean
    )

  def command[A](commands: (A => RowCommand)*)(implicit messages: Messages): Col[A] = {
    Col.fromOptionalField[A](
      name = "",
      maybeFieldValueMapper = (field: A) =>
        commands
          .map(_(field))
          .flatMap { rowCommand =>
            if (rowCommand.show) {
              Some {
                s"<a href='${rowCommand.route}' class='btn btn-default' ${getConfirmationAttribute(
                    rowCommand
                  )} ${getTargetAttribute(rowCommand)}>${rowCommand.title}</a>"
              }
            } else {
              None
            }
          } match {
          case Nil =>
            None

          case nonEmpty =>
            Some(s"""<div class='btn-group'>${nonEmpty.mkString}</div>""")
        },
      sortExpression = "",
      cssClass       = _ => Some("command-column")
    )

  }

  def getConfirmationAttribute(rowCommand: RowCommand)(implicit messages: Messages): String = {
    if (rowCommand.confirmationMessage != "") {
      val message = Messages(rowCommand.confirmationMessage)
      s"""onclick="return confirm('$message')""""
    } else {
      ""
    }
  }

  def getTargetAttribute(rowCommand: RowCommand): String = rowCommand.openInNewWindow match {
    case true  => "target=\"_blank\""
    case false => ""
  }

}

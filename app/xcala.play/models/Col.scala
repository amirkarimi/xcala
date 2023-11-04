package xcala.play.models

import play.api.i18n.Messages

final case class Col[A](
    name          : String,
    fieldMapper   : A => String,
    sortExpression: Any                 = "",
    cssClass      : A => Option[String] = (_: A) => None,
    headerCssClass: Option[String]      = None
)

object Col {

  def command[A](commands: (A => RowCommand)*)(implicit messages: Messages): Col[A] = {
    Col[A](
      name           = "",
      fieldMapper    = (field: A) =>
        "<div class='btn-group'>" +
        commands
          .map(_(field))
          .map { rowCommand =>
            if (rowCommand.show) {
              s"<a href='${rowCommand.route}' class='btn btn-default' ${getConfirmationAttribute(rowCommand)} ${getTargetAttribute(rowCommand)}>${rowCommand.title}</a>"
            } else {
              ""
            }
          }
          .mkString +
        "</div>",
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

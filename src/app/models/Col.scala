package xcala.play.models

import play.twirl.api.Html
import play.api.mvc.Call
import play.api.i18n.Messages

case class Col[A](name: String, fieldMapper: A => String, sortExpression: String = "", cssClass: A => Option[String] = ((a: A) => None), headerCssClass: Option[String] = None)

object Col {
  def command[A](commands: (A => RowCommand)*) = {
    Col[A]("",
      (field: A) =>
	      "<div class='btn-group'>" +
	      commands.map(_(field)).map { rowCommand =>
          if (rowCommand.show) {
            s"<a href='${rowCommand.route}' class='btn btn-default' ${getConfirmationAttribute(rowCommand)} ${getTargetAttribute(rowCommand)}>${rowCommand.title}</a>"
          } else {
            ""
          }
	    	}.mkString +
	      "</div>",
      "",
      _ => Some("command-column"))
  }
  
  def getConfirmationAttribute(rowCommand: RowCommand): String = {
    if (rowCommand.confirmationMessage != "") {
      val message = Messages(rowCommand.confirmationMessage)
      s"""onclick="return confirm('$message')""""
    } else {
      ""
    }
  } 
  
  def getTargetAttribute(rowCommand: RowCommand): String = rowCommand.openInNewWindow match {
    case true => "target=\"_blank\""
    case false => ""
  }
}

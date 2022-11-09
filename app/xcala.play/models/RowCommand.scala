package xcala.play.models

import play.api.mvc.Call

final case class RowCommand(
    route: Call,
    title: String,
    confirmationMessage: String = "",
    openInNewWindow: Boolean = false,
    show: Boolean = true
)

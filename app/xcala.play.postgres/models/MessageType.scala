package xcala.play.postgres.models

object MessageType {
  type Type = String

  val Success: String = "success"
  val Info: String    = "info"
  val Warning: String = "warning"
  val Danger: String  = "danger"
}

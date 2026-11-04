package xcala.play.models

object TaskOwner extends Options {
  type Type = String

  val MessagePrefix: String = "taskOwner."

  val Admin    : String = "admin"
  val Exhibitor: String = "exhibitor"

  val all: Seq[String] = Seq(
    Admin,
    Exhibitor
  )

}

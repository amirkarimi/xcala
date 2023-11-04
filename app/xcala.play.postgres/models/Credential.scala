package xcala.play.postgres.models

trait Credential {
  def username  : String
  def password  : String
  def isDisabled: Boolean
}

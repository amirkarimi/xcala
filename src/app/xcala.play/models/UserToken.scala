package xcala.play.models

import play.api.mvc.CookieBaker
import scala.util.Random
import play.api.Play
import play.api.mvc.Cookie

case class UserToken(username: String, remember: Boolean, accountType: String, token: Long) {
  def renewToken: UserToken = {
    copy(token = UserToken.newToken)
  }
}

object UserToken {
  def newToken = Random.nextLong()
  
  def apply(username: String, remember: Boolean, accountType: String): UserToken = {
    UserToken(username, remember, accountType, newToken)
  }
}

case class UserTokenCookieBaker(accountType: String) extends CookieBaker[UserToken] {
  val COOKIE_NAME = "au_u_" + accountType
  val UserNameKey = "au_user"
  val UserTokenKey = "au_token"
  val UserRememberKey = "au_rem"
  val UserAccTypeKey = "au_acctype"
  val DefaultMaxAge = 60*60*24*365 // Set the cookie max age to 1 year

  val emptyCookie = UserToken("", false, "", 0)

  override val isSigned = true
  override val maxAge = Some(Play.maybeApplication.flatMap(_.configuration.getInt("rememberMe.maxAge")).getOrElse(DefaultMaxAge))

  def deserialize(data: Map[String, String]) = {
    UserToken(
      data(UserNameKey), 
      data(UserRememberKey).toBoolean,
      data(UserAccTypeKey).toString,
      data(UserTokenKey).toLong)
  }

  def serialize(userToken: UserToken) = {
    Map(
      UserNameKey -> userToken.username, 
      UserRememberKey -> userToken.remember.toString,
      UserAccTypeKey -> userToken.accountType,
      UserTokenKey -> userToken.token.toString)
  }
  
  override def encodeAsCookie(data: UserToken): Cookie = {
    val originalCookie = super.encodeAsCookie(data)
    if (data.remember) {
      originalCookie.copy(maxAge = maxAge)
    } else {
      originalCookie.copy(maxAge = None)
    }
  }  
}

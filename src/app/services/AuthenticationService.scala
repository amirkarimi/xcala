package xcala.play.services

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import play.api.mvc.Request
import play.api.mvc.RequestHeader
import xcala.play.models._
import reactivemongo.core.commands.LastError
import reactivemongo.bson.BSONObjectID

abstract class AuthenticationService[A <: Credential](accountService: AccountService[A], userTokenService: UserTokenService) {
  
  def accountType: String
  
  def getAccountIdentity(request: RequestHeader): Future[Option[A]] = {
    val usertokenFuture = validateUserToken(request)
    
    val identityFuture = for {
      userToken <- usertokenFuture
    } yield {
      val usernameOpt = userToken.map(_.username)
	    usernameOpt map { username =>
        accountService.getAccount(username)
	    } getOrElse Future.successful(None)
    }
    
    // I wanted `identityFuture.flatten` it isn't available. This is the alternative
    identityFuture flatMap identity
  }
  
  def renewUserToken(request: RequestHeader): Future[Option[UserToken]] = {
    validateUserToken(request) flatMap {
      case None => Future.successful(None)
      case Some(userToken) => userTokenService.renew(userToken).map(Some(_))
    }
  }
  
  def saveUserToken(username: String, remember: Boolean): Future[UserToken] = {
    val userToken = UserToken(username, remember, accountType)
    val userTokenWithAccType = userToken.copy(accountType = accountType)    
    userTokenService.save(userTokenWithAccType) map { _ =>
      userToken
    }
  }
  
  private def validateUserToken(request: RequestHeader): Future[Option[UserToken]] = {
    try {
      val userToken = UserToken.decodeFromCookie(request.cookies.get(UserToken.COOKIE_NAME))
      userTokenService.validateToken(userToken)
    } catch {
      case ex: NoSuchElementException => Future.successful(None)
    }
  }
}

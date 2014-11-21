package xcala.play.controllers

import xcala.play.models.{UserToken, Credential}
import play.api.i18n.Lang
import play.api.mvc._
import xcala.play.services.AuthenticationService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

abstract class Authenticated[A, B <: Credential](val authenticationService: AuthenticationService[B], val authorizationRules: Seq[AuthorizationRule[B]], val action: Action[A]) extends Action[A] {

  def apply(request: Request[A]): Future[Result] = {
    val accountFuture = authenticationService.getAccountIdentity(request)

    val resultFuture = accountFuture flatMap {
      // No user found; redirect to sign in page
      case None => noUserResult(request)
      // User found; check authorization
      case Some(account) => authorizeUserResult(account, request)
    }

    resultFuture flatMap { result =>
      authenticationService.renewUserToken(request) map {
        // There is no token to be renewed
        case None => result
        // There was a token and renewed so update the cookie
        case Some(userToken) => result.withCookies(UserToken.encodeAsCookie(userToken))
      }
    }
  }

  def authorizeUserResult(account: B, request: Request[A]): Future[Result] = {
    val areRulesSatisfied = authorizationRules.forall(_.isAuthorized(account, request))
    areRulesSatisfied match {
      // User authorized; do the action
      case true => action(request)
      // User not authorized; show access denied page
      case false => accessDenied(request)
    }
  }

  def noUserResult(request: Request[A]): Future[Result]

  def accessDenied(request: Request[A]): Future[Result]

  lazy val parser = action.parser
}

trait AuthorizationRule[A] {
  def isAuthorized(account: A, request: Request[_]): Boolean
}

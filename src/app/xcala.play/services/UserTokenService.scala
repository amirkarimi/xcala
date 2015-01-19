package xcala.play.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import xcala.play.models._
import reactivemongo.bson._
import reactivemongo.bson.Macros
import scala.util.Random
import reactivemongo.core.commands.LastError

class UserTokenService(implicit val ec: ExecutionContext) extends DataCrudService[UserToken] {
  val collectionName = "userTokens"

  val documentHandler = Macros.handler[UserToken]
  
  def validateToken(userToken: UserToken): Future[Option[UserToken]] = {
    findOne(userToken)
  }
  
  def renew(userToken: UserToken): Future[UserToken] = {
    val newToken = userToken.renewToken
    save(newToken).map(_ => newToken)
  }
  
  def findOne(userToken: UserToken): Future[Option[UserToken]] = {
    val query = getQuery(userToken)
    findOne(query)
  }
  
  override def save(model: UserToken): Future[BSONObjectID] = {
    // Remove old token if exists
    remove(BSONDocument("username" -> model.username, "accountType" -> model.accountType)) flatMap { _ =>
      // Save new one
      super.save(model)
    }
  }
  
  private def getQuery(userToken: UserToken) = BSONDocument("username" -> userToken.username, "accountType" -> userToken.accountType, "token" -> userToken.token) 
}

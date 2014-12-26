package xcala.play.services

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import xcala.play.models._
import org.mindrot.jbcrypt.BCrypt
import reactivemongo.bson.BSONObjectID

trait AccountService[A <: Credential] {
  def getAccount(username: String): Future[Option[A]]
  
  def changePassword(account: A, newPassword: String): Future[Boolean]
  
  def checkAccount(username: String, plainPassword: String): Future[Boolean] = {
    getAccount(username) map {
      case Some(credential) if !credential.isDisabled && BCrypt.checkpw(plainPassword, credential.password) => true 
      case _ => false
    }
  }

  def changeAccountPassword(username: String, changePasswordModel: ChangePassword): Future[Boolean] = {
    getAccount(username) flatMap { 
      case Some(credential) if !credential.isDisabled && BCrypt.checkpw(changePasswordModel.curPassword, credential.password) =>
        val newPassword = hashPassword(changePasswordModel.newPassword)
        changePassword(credential, newPassword)
      case _ => 
        Future.successful(false)
    }
  }
  
  def hashPassword(password: String) = BCrypt.hashpw(password, BCrypt.gensalt())
}

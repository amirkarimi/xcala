package xcala.play.services

import xcala.play.helpers.FutureHelpers._

import akka.actor.ActorSystem
import play.api._
import play.api.i18n.Lang
import play.api.i18n.LangImplicits
import play.api.i18n.Messages
import play.api.i18n.MessagesApi
import play.api.inject.Injector
import play.api.inject.guice.GuiceApplicationBuilder

import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.util.Random

import org.specs2.execute.AsResult
import org.specs2.execute.Result
import org.specs2.mutable.Around

class WithTestDb(hostName: String) extends Around with LangImplicits {

  val application                   : Application      = GuiceApplicationBuilder().build()
  implicit lazy val implicitInjector: Injector         = instanceOf[Injector]
  implicit lazy val executionContext: ExecutionContext = instanceOf[ExecutionContext]
  implicit val configuration        : Configuration    = application.configuration
  implicit lazy val system          : ActorSystem      = application.actorSystem

  implicit val databaseConfig: DatabaseConfig = new DatabaseConfig {
    implicit val ec      : ExecutionContext = executionContext
    override def mongoUri: String           = s"mongodb://$hostName/xcala-test-${Math.abs(Random.nextInt())}"
  }

  def instanceOf[T: ClassTag]: T = application.injector.instanceOf[T]

  override def messagesApi: MessagesApi = instanceOf[MessagesApi]
  implicit val lang       : Lang        = Lang("fa")
  lazy val messages       : Messages    = lang2Messages

  def around[T](t: => T)(implicit ev: AsResult[T]): Result = {
    ev.asResult {
      val result = t
      databaseConfig.databaseFuture.map { db =>
        db.drop().awaitReady()
      }

      result
    }
  }

}

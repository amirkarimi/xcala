package xcala.play.services

import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import play.api.inject.guice.GuiceApplicationBuilder
import play.api._
import play.api.i18n.{Lang, LangImplicits, Messages, MessagesApi}

import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Random
import xcala.play.helpers.FutureHelpers._

import scala.reflect.ClassTag

trait WithTestDb extends Around with LangImplicits {

  val dbConfiguration = Map(
    "mongodb.uri" -> s"mongodb://localhost/xcala-test-${Math.abs(Random.nextInt())}"
  )

  val application: Application = GuiceApplicationBuilder().configure(dbConfiguration).build()
  implicit val configuration: Configuration = application.configuration

  implicit val databaseConfig = new DefaultDatabaseConfig {
    implicit val configuration: Configuration = application.configuration
  }

  def instanceOf[T: ClassTag] = application.injector.instanceOf[T]

  override def messagesApi: MessagesApi = instanceOf[MessagesApi]
  implicit val lang = Lang("fa")
  lazy val messages: Messages = lang2Messages

  def around[T](t: => T)(implicit ev: AsResult[T]): Result = {
    ev.asResult {
      val result = t
      databaseConfig.databaseFuture map { db =>
        db.drop().awaitReady
      }

      result
    }
  }
}

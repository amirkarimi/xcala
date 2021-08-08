package scala.xcala.play.services

import org.specs2.execute.{AsResult, Result}
import org.specs2.mutable.Around
import play.api.{Configuration, Play}
import xcala.play.services.DefaultDatabaseConfig
import scala.concurrent.ExecutionContext.Implicits._
import scala.util.Random
import scala.xcala.play.helpers.FutureHelpers._

trait WithTestDb extends Around {
  allowGlobalApplication = true
  implicit val configuration: Configuration = Play.current.configuration

  implicit val databaseConfig = new DefaultDatabaseConfig {
    override lazy val mongoUri: String = "mongodb://localhost/xcala-test-" + Math.abs(Random.nextInt())
    implicit val configuration: Configuration = Play.current.configuration
  }

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

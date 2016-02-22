package scala.xcala.play.services

import org.specs2.execute.{Result, AsResult}
import org.specs2.mutable.Around
import xcala.play.services.DefaultDatabaseConfig

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Await
import scala.util.Random
import scala.xcala.play.helpers.FutureHelpers._

trait WithTestDb extends Around {

  implicit val databaseConfig = new DefaultDatabaseConfig {
    override def mongoUri: String = "mongodb://localhost/xcala-test-" + Math.abs(Random.nextInt())
  }

  def around[T](t: => T)(implicit ev: AsResult[T]): Result = {
    ev.asResult {
      val result = t
      databaseConfig.db.drop().awaitReady
      result
    }
  }
}

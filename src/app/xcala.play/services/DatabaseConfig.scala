package xcala.play.services

import scala.concurrent.Future
import reactivemongo.api._
import play.api._
import xcala.play.utils.WithExecutionContext

trait DatabaseConfig {
  def mongoUri: String
  def parsedUriFuture: Future[MongoConnection.ParsedURI]
  def driver: AsyncDriver
  def connectionFuture: Future[MongoConnection]
  def databaseFuture: Future[DB]
}

trait DefaultDatabaseConfig extends DatabaseConfig with WithExecutionContext {
  val configuration: Configuration
  lazy val mongoUri = configuration.get[String]("mongodb.uri")
  lazy val parsedUriFuture: Future[MongoConnection.ParsedURI] = MongoConnection.fromString(mongoUri)
  lazy val driver: AsyncDriver = AsyncDriver()
  lazy val connectionFuture = parsedUriFuture.flatMap(p => driver.connect(p))
  lazy val databaseFuture: Future[DB] = {
    parsedUriFuture flatMap { parsedUri =>
      connectionFuture.flatMap(_.database(parsedUri.db.get))
    }
  }
}

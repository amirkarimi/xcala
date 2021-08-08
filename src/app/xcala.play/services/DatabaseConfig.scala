package xcala.play.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import reactivemongo.api._
import play.api._

trait DatabaseConfig {
  def mongoUri: String
  def parsedUriFuture(implicit ec: ExecutionContext): Future[MongoConnection.ParsedURI]
  
  def driver: AsyncDriver
  def connectionFuture(implicit ec: ExecutionContext): Future[MongoConnection]
  def databaseFuture(implicit ec: ExecutionContext): Future[DB]
}

trait DefaultDatabaseConfig extends DatabaseConfig {
  val configuration: Configuration
  lazy val mongoUri = configuration.get[String]("mongodb.uri")
  def parsedUriFuture(implicit ec: ExecutionContext): Future[MongoConnection.ParsedURI] = MongoConnection.fromString(mongoUri)
  lazy val driver: AsyncDriver = new AsyncDriver
  def connectionFuture(implicit ec: ExecutionContext) = parsedUriFuture.flatMap(p => driver.connect(p))
  def databaseFuture(implicit ec: ExecutionContext): Future[DB] = {
    parsedUriFuture flatMap { parsedUri =>
      connectionFuture.flatMap(_.database(parsedUri.db.get))
    }
  }
}

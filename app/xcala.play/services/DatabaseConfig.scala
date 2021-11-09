package xcala.play.services

import scala.concurrent.Future
import reactivemongo.api._
import xcala.play.utils.WithExecutionContext

trait DatabaseConfig extends WithExecutionContext {
  def mongoUri: String
  lazy val parsedUriFuture: Future[MongoConnection.ParsedURI] = MongoConnection.fromString(mongoUri)
  lazy val driver: AsyncDriver = AsyncDriver()
  lazy val connectionFuture = parsedUriFuture.flatMap(p => driver.connect(p))
  lazy val databaseFuture: Future[DB] = parsedUriFuture flatMap { parsedUri =>
    connectionFuture.flatMap(_.database(parsedUri.db.get))
  }
}

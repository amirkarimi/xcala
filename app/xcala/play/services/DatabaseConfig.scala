package xcala.play.services

import xcala.play.utils.WithExecutionContext

import scala.concurrent.Future

import reactivemongo.api._

trait DatabaseConfig extends WithExecutionContext {
  def mongoUri                     : String
  private lazy val parsedUriFuture : Future[MongoConnection.ParsedURI] = MongoConnection.fromString(mongoUri)
  private lazy val driver          : AsyncDriver                       = AsyncDriver()
  private lazy val connectionFuture: Future[MongoConnection]           = parsedUriFuture.flatMap(p => driver.connect(p))

  lazy val databaseFuture: Future[DB] = parsedUriFuture.flatMap { parsedUri =>
    connectionFuture.flatMap(_.database(parsedUri.db.get))
  }

}

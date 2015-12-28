package xcala.play.services

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import reactivemongo.bson._
import reactivemongo.api._
import reactivemongo.core.commands._
import reactivemongo.api.collections.default._
import reactivemongo.api.collections._
import play.api.libs.iteratee.Enumerator
import xcala.play.models._
import xcala.play.extensions.BSONHandlers._
import reactivemongo.api.gridfs.GridFS
import org.joda.time.DateTime
import xcala.play.utils.WithExecutionContext

trait DBConfig {
  def mongoUri: String
  def parsedUri: MongoConnection.ParsedURI
  
  def driver: MongoDriver
  def connection: MongoConnection
  def db(implicit ex: ExecutionContext): DefaultDB
}

trait DefaultDBConfig extends DBConfig {
  lazy val parsedUri: MongoConnection.ParsedURI = MongoConnection.parseURI(mongoUri).get

  lazy val driver: MongoDriver = new MongoDriver
  lazy val connection: MongoConnection = driver.connection(parsedUri)
  def db(implicit ex: ExecutionContext): DefaultDB = connection.db(parsedUri.db.get)
}

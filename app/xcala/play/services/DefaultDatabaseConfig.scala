package xcala.play.services

import play.api.Configuration

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.ExecutionContext

@Singleton
class DefaultDatabaseConfig @Inject() (
    configuration  : Configuration,
    implicit val ec: ExecutionContext
) extends DatabaseConfig {
  override def mongoUri: String = configuration.get[String]("mongodb.uri")
}

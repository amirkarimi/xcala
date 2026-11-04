package xcala.play.services

import xcala.play.services.DefaultDatabaseConfig

import javax.inject.Inject
import javax.inject.Singleton
import scala.concurrent.{ExecutionContext, Future}

import reactivemongo.api.DB
import reactivemongo.api.bson._
import reactivemongo.api.bson.collection._

@Singleton
class ActorVolunteeringService @Inject() (
    databaseConfig: DefaultDatabaseConfig
)(implicit
    val ec: ExecutionContext
) {

  private lazy val dbFuture: Future[DB] = databaseConfig.databaseFuture

  private val collectionName: String = "actorVolunteer"

  private def getCollection: Future[BSONCollection] = {
    dbFuture.map(_.collection(collectionName)).map { coll =>
      coll
    }
  }

  private lazy val collectionFuture: Future[BSONCollection] = getCollection

  def volunteerFor(uniqueKey: String): Future[Boolean] =
    collectionFuture
      .flatMap { collection =>
        collection.insert.one(BSONDocument("_id" -> uniqueKey)).map(_.n > 0)
      }
      .recoverWith { case _ =>
        Future.successful(false)
      }

}

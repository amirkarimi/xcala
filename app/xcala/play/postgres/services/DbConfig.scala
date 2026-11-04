package xcala.play.postgres.services

import akka.stream.alpakka.slick.scaladsl.SlickSession
import play.api.db.slick.DatabaseConfigProvider

import javax.inject.Inject

import slick.jdbc.JdbcBackend

/** Database configuration information.
  */
class DbConfig @Inject() (
    dbConfigProvider: DatabaseConfigProvider,
    val profile     : xcala.play.postgres.utils.MyPostgresProfile
) {
  val db          : JdbcBackend#Database = dbConfigProvider.get[xcala.play.postgres.utils.MyPostgresProfile].db
  lazy val session: SlickSession         = SlickSession.forDbAndProfile(db, profile)

}

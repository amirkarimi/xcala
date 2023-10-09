package xcala.play.postgres.models

import scala.concurrent.Future

object FutureSeq {

  def apply[A](elems: A*): Future[Seq[A]] = Future.successful {
    elems
  }

}

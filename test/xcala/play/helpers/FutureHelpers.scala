package xcala.play.helpers

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

object FutureHelpers {

  implicit class RichFuture[A](val future: Future[A]) extends AnyVal {
    def awaitResult: A                   = awaitResult(3.seconds)
    def awaitResult(atMost: Duration): A = Await.result(future, atMost)

    def awaitReady: Unit                   = awaitReady(3.seconds)
    def awaitReady(atMost: Duration): Unit = Await.ready(future, atMost)
  }

}

package xcala.play.utils

import xcala.play.helpers.FutureHelpers._
import xcala.play.utils.IO.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.specs2.mutable._

class IOSpec extends Specification {
  import IOSpec._

  "IO" should {

    "run every step of the happy path in order" in {
      val steps = new java.util.concurrent.ConcurrentLinkedQueue[String]

      val io = for {
        a <- IO.fromFuture(Future { Thread.sleep(50); steps.add("first"); 1 })
        _ <- IO.fromFuture(Future(steps.add("second")))
        b  = a + 1
        c <- IO.fromEither[String, Int](Right(b * 10))
      } yield c

      io.run.awaitResult must beRight(20)
      steps.toArray.toSeq must_== Seq("first", "second")
    }

    "skip every later step after the first error" in {
      var laterStepRan = false

      val io = for {
        _ <- IO.fromEither[String, Unit](Left("first"))
        _ <- IO.fromFuture(Future { laterStepRan = true })
      } yield ()

      io.run.awaitResult must beLeft("first")
      laterStepRan must beFalse
    }

    "change the error with leftMap and leave a success alone" in {
      IO.fail("error").leftMap(_.length).run.awaitResult must beLeft(5)
      IO.fromEither[String, Int](Right(1)).leftMap(_.length).run.awaitResult must beRight(1)
    }

    "keep an exception as a failed Future until recover turns it into an error" in {
      val failing = IO.fromFuture(Future.failed[Int](Boom("db down")))

      failing.run.awaitResult must throwA[Boom]
      failing.recover { case Boom(message) => message }.run.awaitResult must beLeft("db down")
    }

    "not touch errors or successes in recover" in {
      IO.fail("error").recover { case _ => "recovered" }.run.awaitResult must beLeft("error")
      IO.pure(1).recover { case _ => "recovered" }.run.awaitResult must beRight(1)
    }

    "turn a missing value into the given error" in {
      IO.fromOption(None)("missing").run.awaitResult must beLeft("missing")
      IO.fromOption(Some(1))("missing").run.awaitResult must beRight(1)
    }

    "make an IO of a Future with toIO, keeping a failure as an exception" in {
      Future.successful(1).toIO.run.awaitResult must beRight(1)
      Future.failed[Int](Boom("db down")).toIO.run.awaitResult must throwA[Boom]
    }

    "make an IO of an Either with toIO" in {
      Left[String, Int]("error").toIO.run.awaitResult must beLeft("error")
      Right[String, Int](1).toIO.run.awaitResult must beRight(1)
    }

    "make an IO of an Option with toIO, given the error for a missing value" in {
      Option.empty[Int].toIO("missing").run.awaitResult must beLeft("missing")
      Option(1).toIO("missing").run.awaitResult must beRight(1)
    }

    "turn a missing value of a Future into an error with toIO and orFail" in {
      Future.successful(Option.empty[Int]).toIO.orFail("missing").run.awaitResult must beLeft("missing")
      Future.successful(Option(1)).toIO.orFail("missing").run.awaitResult must beRight(1)
    }

    "leave the happy path when ensure's condition does not hold" in {
      IO.ensure(condition = false)("refused").run.awaitResult must beLeft("refused")
      IO.ensure(condition = true)("refused").run.awaitResult must beRight(())
    }

    "run ensureWith's alternative only when the condition does not hold" in {
      var alternativeRan = false

      def alternative: IO[String, Nothing] = {
        alternativeRan = true
        IO.fail("refused")
      }

      IO.ensureWith(condition = true)(alternative).run.awaitResult must beRight(())
      alternativeRan must beFalse

      IO.ensureWith(condition = false)(alternative).run.awaitResult must beLeft("refused")
      alternativeRan must beTrue
    }

    "turn a missing value of an IO into the given error with orFail" in {
      IO.pure(Option.empty[Int]).orFail("missing").run.awaitResult must beLeft("missing")
      IO.pure(Option(1)).orFail("missing").run.awaitResult must beRight(1)
      IO.fromEither[String, Option[Int]](Left("earlier")).orFail("missing").run.awaitResult must beLeft("earlier")
    }

    "expose the error or the value as a value with either, so a later step can record it" in {
      IO.fail("error").either.run.awaitResult must beRight(Left("error"))
      IO.pure(1).either.run.awaitResult must beRight(Right(1))
      IO.fromFuture(Future.failed[Int](Boom("db down"))).either.run.awaitResult must throwA[Boom]
    }

    "fold an error and a success into one value" in {
      IO.fail("error").fold(e => s"left $e", (a: Int) => s"right $a").awaitResult must_== "left error"
      IO.pure(1).fold((e: String) => s"left $e", a => s"right $a").awaitResult must_== "right 1"
    }
  }

}

object IOSpec {
  private final case class Boom(message: String) extends Exception(message)
}

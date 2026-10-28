package xcala.play.utils

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

/** A Future that ends in either an error `E` or a value `A`.
  *
  * `flatMap` runs the next step only after a success. The first error skips every later step, so a for/yield over IO
  * reads as the happy path, and each line says which failure it can produce. An exception (a failed Future) is not an
  * error value: it skips the remaining steps too, and stays an exception until `recover` turns it into an `E`.
  *
  * Eager, like the Future inside it: an IO is work already running, not a lazy description as in cats-effect. It is
  * `EitherT[Future, E, A]` without the cats dependency.
  */
final class IO[+E, +A](val run: Future[Either[E, A]]) {

  def map[B](f: A => B)(implicit ec: ExecutionContext): IO[E, B] =
    IO(run.map(_.map(f)))

  def flatMap[E2 >: E, B](f: A => IO[E2, B])(implicit ec: ExecutionContext): IO[E2, B] =
    IO(run.flatMap {
      case Right(a) => f(a).run
      case Left(e)  => Future.successful(Left(e))
    })

  def leftMap[E2](f: E => E2)(implicit ec: ExecutionContext): IO[E2, A] =
    IO(run.map(_.left.map(f)))

  /** Turns an exception into an error value. */
  def recover[E2 >: E](pf: PartialFunction[Throwable, E2])(implicit ec: ExecutionContext): IO[E2, A] =
    IO(run.recover(pf.andThen(Left(_))))

  /** The error or the value as a value, for a step that must handle both, such as recording a result. An exception
    * still fails the Future.
    */
  def either(implicit ec: ExecutionContext): IO[Nothing, Either[E, A]] =
    IO(run.map(Right(_)))

  /** Leaves IO: one function for the error, one for the value. An exception still fails the Future. */
  def fold[B](ifError: E => B, ifValue: A => B)(implicit ec: ExecutionContext): Future[B] =
    run.map(_.fold(ifError, ifValue))

}

object IO {

  def apply[E, A](run: Future[Either[E, A]]): IO[E, A] = new IO(run)

  val unit: IO[Nothing, Unit] = IO(Future.successful(Right(())))

  def pure[A](a: A): IO[Nothing, A] = IO(Future.successful(Right(a)))

  def fail[E](e: E): IO[E, Nothing] = IO(Future.successful(Left(e)))

  def fromEither[E, A](either: Either[E, A]): IO[E, A] = IO(Future.successful(either))

  def fromOption[E, A](option: Option[A])(ifNone: => E): IO[E, A] = fromEither(option.toRight(ifNone))

  /** A plain Future fails only with an exception, never with an `E`. Strict, like Future itself. */
  def fromFuture[A](future: Future[A])(implicit ec: ExecutionContext): IO[Nothing, A] =
    IO(future.map(Right(_)))

  /** Leaves the happy path with `ifFalse` unless `condition` holds. */
  def ensure[E](condition: Boolean)(ifFalse: => E): IO[E, Unit] =
    if (condition) unit else fail(ifFalse)

  /** Like `ensure`, for when leaving the happy path needs a side effect first (a DB write). */
  def ensureWith[E](condition: Boolean)(orElse: => IO[E, Nothing]): IO[E, Unit] =
    if (condition) unit else orElse

  /** `toIO` on the types IO does not own (`Future`, `Either`, `Option`), so code enters IO in one visible step and then
    * uses only IO's methods; plus `orFail` on an `IO` of an `Option`. Explicit method calls, not implicit conversions,
    * and opt-in: `import xcala.play.utils.IO.syntax._`.
    */
  object syntax {

    implicit final class FutureToIO[A](private val future: Future[A]) extends AnyVal {

      /** A failed Future stays an exception in the IO, not an error value. */
      def toIO(implicit ec: ExecutionContext): IO[Nothing, A] = IO.fromFuture(future)

    }

    implicit final class EitherToIO[E, A](private val either: Either[E, A]) extends AnyVal {
      def toIO: IO[E, A] = IO.fromEither(either)
    }

    implicit final class OptionToIO[A](private val option: Option[A]) extends AnyVal {

      /** A missing value becomes the error `ifNone`. */
      def toIO[E](ifNone: => E): IO[E, A] = IO.fromOption(option)(ifNone)

    }

    implicit final class IOOptionOps[E, A](private val io: IO[E, Option[A]]) extends AnyVal {

      /** A missing value becomes the error `ifNone`; an earlier error stays as it is. */
      def orFail[E2 >: E](ifNone: => E2)(implicit ec: ExecutionContext): IO[E2, A] =
        io.flatMap(option => IO.fromOption(option)(ifNone))

    }

  }

}

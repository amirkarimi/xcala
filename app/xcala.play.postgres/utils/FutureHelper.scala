package xcala.play.postgres.utils

object FutureHelper {

  def sequence[A, B](s: Seq[Either[A, B]]): Either[A, Seq[B]] =
    s.foldRight(Right(Nil): Either[A, List[B]]) { (e, acc) =>
      for {
        xs <- acc
        x  <- e
      } yield x :: xs
    }

  def partitionEithers[A, B](es: Seq[Either[A, B]]): (Seq[A], Seq[B]) =
    es.foldRight((Seq.empty[A], Seq.empty[B])) { case (e, (as, bs)) =>
      e.fold(a => (a +: as, bs), b => (as, b +: bs))
    }

  def unroll[A, B](es: Seq[Either[A, B]]): Either[Seq[A], Seq[B]] = {
    val (as, bs) = partitionEithers(es)
    if (as.nonEmpty) Left(as) else Right(bs)
  }

}

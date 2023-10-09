package xcala.play.postgres.models

final case class DataWithTotalCount[A](data: Seq[A], totalCount: Int)

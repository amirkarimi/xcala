package xcala.play.models

final case class DataWithTotalCount[A](data: Seq[A], totalCount: Long)

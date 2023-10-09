package xcala.play.postgres.models

final case class MultilangModel[A](lang: String, value: A)

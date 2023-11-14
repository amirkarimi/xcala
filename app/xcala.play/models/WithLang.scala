package xcala.play.models

trait WithLang {
  this: DocumentWithId =>
  def lang: String
}

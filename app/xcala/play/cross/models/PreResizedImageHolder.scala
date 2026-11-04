package xcala.play.cross.models

trait PreResizedImageHolder[Id] {
  def maybeImageFileId: Option[Id]
}

package xcala.play.cross.models

sealed trait ImageRenderType

case object Original extends ImageRenderType

trait ImageResizedRenderType extends ImageRenderType {
  val suffix         : String
  val overriddenWidth: Int

  def resizedObjectName(originalObjectName: String): String =
    originalObjectName + "__" + suffix

}

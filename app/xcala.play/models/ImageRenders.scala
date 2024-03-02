package xcala.play.models

import play.api.mvc.QueryStringBindable

object ImageRenders {

  sealed trait ImageRenderType

  case object Original extends ImageRenderType

  sealed trait ImageResizedRenderType extends ImageRenderType {
    val suffix         : String
    val overriddenWidth: Int

    def resizedObjectName(originalObjectName: String): String =
      originalObjectName + "__" + suffix

  }

  object ImageResizedRenderType {

    def all: Set[ImageResizedRenderType] =
      Set(ExtraSmall, Small, Medium)

  }

  case object ExtraSmall extends ImageResizedRenderType {
    val suffix         : String = "xs"
    val overriddenWidth: Int    = 100

  }

  case object Small extends ImageResizedRenderType {
    val suffix         : String = "sm"
    val overriddenWidth: Int    = 300

  }

  case object Medium extends ImageResizedRenderType {
    val suffix         : String = "md"
    val overriddenWidth: Int    = 450
  }

  implicit def imageRenderBinder: QueryStringBindable[ImageRenderType] =
    new QueryStringBindable[ImageRenderType] {

      def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ImageRenderType]] =
        params.get(key).getOrElse(Nil).headOption match {
          case Some(ExtraSmall.suffix) => Some(Right(ExtraSmall))
          case Some(Small.suffix)      => Some(Right(Small))
          case Some(Medium.suffix)     => Some(Right(Medium))
          case _                       => Some(Right(Original))
        }

      def unbind(key: String, value: ImageRenderType): String =
        value match {
          case Original => ""
          case imageResizedRenderType: ImageResizedRenderType =>
            s"$key=${imageResizedRenderType.suffix}"

        }

    }

}

package xcala.play.extensions

import xcala.play.models.MultilangModel

import play.api.data._
import play.api.data.Forms._

object Forms {
  def multilangText: Mapping[List[MultilangModel[String]]] = multilangMapping(nonEmptyText)

  def multilangMapping[A](valueMapping: Mapping[A]): Mapping[List[MultilangModel[A]]] = {
    list(
      mapping(
        "lang"  -> nonEmptyText,
        "value" -> valueMapping
      )(MultilangModel.apply[A])(MultilangModel.unapply[A])
    )
  }

}

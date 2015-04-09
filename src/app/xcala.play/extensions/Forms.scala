package xcala.play.extensions

import play.api.data._
import play.api.data.Forms._
import xcala.play.models.MultilangText

object Forms {
  def multilangText: Mapping[List[MultilangText]] = multilangText(nonEmptyText)
  
  def multilangText(textMapping: Mapping[String]): Mapping[List[MultilangText]] = {
    list(
      mapping(
        "lang" -> nonEmptyText, 
        "value" -> textMapping
      )(MultilangText.apply)(MultilangText.unapply)
    )
  }
}
package views.html.xcala.play.bootstrap3

import views.html.helper.FieldConstructor

object DefaultHelpers {

  implicit val horizontalField: FieldConstructor =
    FieldConstructor(views.html.xcala.play.bootstrap3.horizontalField(_))

  implicit val fieldWithoutLabel: FieldConstructor = FieldConstructor(
    views.html.xcala.play.bootstrap3.fieldWithoutLabel(_)
  )

}

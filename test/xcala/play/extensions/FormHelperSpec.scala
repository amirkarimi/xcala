package xcala.play.extensions

import org.specs2.mutable._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import FormHelper._
import xcala.play.services.WithTestDb

class FormHelperSpec extends Specification {

  val form: Form[(String, String)] = Form(
    tuple(
      "field1" -> of[String],
      "field2" -> of[String]
    )
  )

  "FixedLanguageForm" should {
    "return correct persian words on arabic inputs" >> new WithTestDb {

        implicit override val lang: Lang = Lang("fa")

      val data: Map[String, String] = Map(
        "field1" -> "كتاب",
        "field2" -> "فارسي"
      )

      val boundForm: Form[(String, String)] = form.bind(data)
      val correctedForm: Form[(String, String)] = boundForm.fixLanguageChars

      val expectedData: (String, String) = ("کتاب", "فارسی")
      correctedForm.value must beSome(expectedData)
    }
  }

}

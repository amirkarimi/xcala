package xcala.play.extensions

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.i18n.Lang
import play.api.test._
import play.api.test.Helpers._

import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import FormHelper._

@RunWith(classOf[JUnitRunner])
class FormHelperSpec extends Specification {
  val form = Form(
    tuple(
      "field1" -> of[String],
      "field2" -> of[String]
    )
  )
  
  "FixedLanguageForm" should {
    "return correct persian words on arabic inputs" in {
      implicit val lang = Lang("fa")

      val data = Map(
        "field1" -> "كتاب",
        "field2" -> "فارسي"
      )

      val boundForm = form.bind(data)
      val correctedForm = boundForm.fixLanguageChars

      val expectedData = ("کتاب", "فارسی")
      correctedForm.value mustEqual Some(expectedData)
    }
  }
  
}

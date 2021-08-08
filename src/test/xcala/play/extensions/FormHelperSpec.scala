package xcala.play.extensions

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.i18n._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import FormHelper._
import org.specs2.execute.{AsResult, Result}
import play.api.Application
import xcala.play.services.WithTestDb

import scala.reflect.ClassTag

class FormHelperSpec extends Specification {
  val form = Form(
    tuple(
      "field1" -> of[String],
      "field2" -> of[String]
    )
  )
  
  "FixedLanguageForm" should {
    "return correct persian words on arabic inputs" >> new WithTestDb {

      implicit override val lang = Lang("fa")

      val data = Map(
        "field1" -> "كتاب",
        "field2" -> "فارسي"
      )

      val boundForm = form.bind(data)
      val correctedForm = boundForm.fixLanguageChars

      val expectedData = ("کتاب", "فارسی")
      correctedForm.value must beSome(expectedData)
    }
  }
}

package xcala.play.extensions

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.i18n._
import play.api.test._
import play.api.test.Helpers._
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import FormHelper._
import play.api.inject.NewInstanceInjector.instanceOf

@RunWith(classOf[JUnitRunner])
class FormHelperSpec extends Specification with LangImplicits {

  override def messagesApi: MessagesApi = instanceOf[MessagesApi]
  implicit val lang = Lang("fa")
  lazy val messages: Messages = lang2Messages

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
      correctedForm.value must beSome(expectedData)
    }
  }
  
}

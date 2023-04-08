package xcala.play.extensions

import FormHelper._
import xcala.play.services.WithTestDb

import play.api
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.i18n._

import java.io.File

import com.typesafe.config.ConfigFactory
import org.specs2.main.CommandLine
import org.specs2.mutable._

class FormHelperSpec(cmd: CommandLine) extends Specification {

  val configFilePath =
    cmd.arguments.find(_.startsWith("-Dtest.config")).map(_.split("=")(1)).getOrElse("./conf/local-test.conf")

  val configuration: Configuration =
    Configuration {

      ConfigFactory
        .parseFile(new File(configFilePath))
        .withFallback(
          Configuration
            .load(api.Environment.simple(new File("."), api.Mode.Test))
            .underlying
        )
        .resolve()

    }

  val hostName = configuration.get[String]("mongodbHost")

  val form: Form[(String, String)] = Form(
    tuple(
      "field1" -> of[String],
      "field2" -> of[String]
    )
  )

  "FixedLanguageForm" should {
    "return correct persian words on arabic inputs" >> new WithTestDb(hostName) {

      implicit override val lang: Lang = Lang("fa")

      val data: Map[String, String] = Map(
        "field1" -> "كتاب",
        "field2" -> "فارسي"
      )

      val boundForm: Form[(String, String)]     = form.bind(data)
      val correctedForm: Form[(String, String)] = boundForm.fixLanguageChars

      val expectedData: (String, String) = ("کتاب", "فارسی")
      correctedForm.value must beSome(expectedData)
    }
  }

}

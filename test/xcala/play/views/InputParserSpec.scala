package xcala.play.views

import views.html.xcala.play.bootstrap3._

import org.junit.runner._
import org.specs2.mutable._
import org.specs2.runner._

@RunWith(classOf[JUnitRunner])
class InputParserSpec extends Specification {

  "InputParser" should {
    val className = "form-control"

    "adds class to text input without class" in {
      val rawInput       = """<input type="text" name="test" id="test">"""
      val result         = InputParser.addClass(rawInput, className)
      val inputWithClass = s"""<input type="text" name="test" id="test" class="$className">"""
      result mustEqual inputWithClass
    }

    "adds class to text input with class" in {
      val rawInput       = """<input type="text" name="test" id="test" class="test">"""
      val result         = InputParser.addClass(rawInput, className)
      val inputWithClass = s"""<input type="text" name="test" id="test" class="$className test">"""
      result mustEqual inputWithClass
    }

    "don't touch input if it's not input" in {
      val rawInput = """<something type="text" name="test" id="test">"""
      val result   = InputParser.addClass(rawInput, className)
      result mustEqual rawInput
    }

    "adds class to multiline inputs" in {
      val rawInput = """<select name="test" id="test">
            <option value="1">1</option>
          </select>
        """
      val result   = InputParser.addClass(rawInput, className)
      val inputWithClass = s"""<select name="test" id="test" class="$className">
            <option value="1">1</option>
          </select>
        """
      result mustEqual inputWithClass
    }
  }

}

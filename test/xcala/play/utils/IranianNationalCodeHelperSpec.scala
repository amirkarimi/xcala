package xcala.play.utils

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import org.joda.time.DateTime

@RunWith(classOf[JUnitRunner])
class IranianNationalCodeHelperSpec extends Specification {

  "isValid method" should {

    "return true if national code is valid" in {
      IranianNationalCodeHelper.isValid("0079884261") must beTrue
      IranianNationalCodeHelper.isValid("0000000000") must beTrue
      IranianNationalCodeHelper.isValid("1111111111") must beTrue
    }

    "return false if national code is invalid" in {
      IranianNationalCodeHelper.isValid("007988426")  must beFalse
      IranianNationalCodeHelper.isValid("0000100000") must beFalse
      IranianNationalCodeHelper.isValid("1111121111") must beFalse
    }
  }
}

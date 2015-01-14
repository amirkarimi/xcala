package xcala.play.extensions

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import org.joda.time.DateTime

import xcala.play.models.Range
import reactivemongo.bson._

@RunWith(classOf[JUnitRunner])
class BSONHandlersSpec extends Specification {

  "Optional range handler" should {
    import BSONHandlers._
    val handler = BSONHandlers.optionalRangeHandler[Int]

    "write Option types with Some value" in {
      val model = Range[Option[Int]](Some(1), Some(2))
      val bson = handler.write(model)
      bson === BSONDocument("from" -> 1, "to" -> 2)
    }

    "write Option types with None value" in {
      val model = Range[Option[Int]](None, None)
      val bson = handler.write(model)
      bson === BSONDocument()
    }
    
    "read Option types with Some value" in {
      val bson = BSONDocument("from" -> 1, "to" -> 2)
      val model = handler.read(bson)
      model === Range[Option[Int]](Some(1), Some(2))
    }
    
    "read Option types with None value" in {
      val bson = BSONDocument()
      val model = handler.read(bson)
      model === Range[Option[Int]](None, None)
    }
  }

  "Range handler" should {
    import BSONHandlers._
    val handler = BSONHandlers.rangeHandler[Int]

    "write correctly" in {
      val model = Range[Int](1, 2)
      val bson = handler.write(model)
      bson === BSONDocument("from" -> 1, "to" -> 2)
    }

    "read correctly" in {
      val bson = BSONDocument("from" -> 1, "to" -> 2)
      val model = handler.read(bson)
      model === Range[Int](1, 2)
    }
  }
}

package xcala.play.extensions

import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._
import play.api.test._
import play.api.test.Helpers._
import org.joda.time.DateTime

import xcala.play.models.MultilangModel
import xcala.play.models.Range
import reactivemongo.api.bson._

@RunWith(classOf[JUnitRunner])
class BSONHandlersSpec extends Specification {

  "Optional range handler" should {
    val handler = BSONHandlers.optionalRangeHandler[Int]

    "write Option types with Some value" in {
      val model = Range[Option[Int]](Some(1), Some(2))
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument("from" -> 1, "to" -> 2)
    }

    "write Option types with None value" in {
      val model = Range[Option[Int]](None, None)
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument()
    }

    "read Option types with Some value" in {
      val bson  = BSONDocument("from" -> 1, "to" -> 2)
      val model = handler.readOpt(bson).get
      model === Range[Option[Int]](Some(1), Some(2))
    }

    "read Option types with None value" in {
      val bson  = BSONDocument()
      val model = handler.readOpt(bson).get
      model === Range[Option[Int]](None, None)
    }
  }

  "Range handler" should {
    val handler = BSONHandlers.rangeHandler[Int]

    "write correctly" in {
      val model = Range[Int](1, 2)
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument("from" -> 1, "to" -> 2)
    }

    "read correctly" in {
      val bson  = BSONDocument("from" -> 1, "to" -> 2)
      val model = handler.readOpt(bson).get
      model === Range[Int](1, 2)
    }
  }

  "Multilang handler with String" should {
    val handler = BSONHandlers.multilangHandler[String]

    "write correctly" in {
      val model = MultilangModel[String]("en", "Test")
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument("lang" -> "en", "value" -> "Test")
    }

    "read correctly" in {
      val bson  = BSONDocument("lang" -> "en", "value" -> "Test")
      val model = handler.readOpt(bson).get
      model === MultilangModel[String]("en", "Test")
    }
  }

  "Multilang handler with BSONObjecID" should {
    val handler      = BSONHandlers.multilangDocumentHandler[BSONObjectID]
    val bsonObjectId = BSONObjectID.generate

    "write correctly" in {
      val model = MultilangModel[BSONObjectID]("en", bsonObjectId)
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument(Seq("lang" -> BSONString("en"), "value" -> bsonObjectId))
    }

    "read correctly" in {
      val bson  = BSONDocument(Seq("lang" -> BSONString("en"), "value" -> bsonObjectId))
      val model = handler.readOpt(bson).get
      model === MultilangModel[BSONObjectID]("en", bsonObjectId)
    }
  }

  "Multilang handler with Option[BSONObjecID]" should {
    val handler      = BSONHandlers.optionalMultilangDocumentHandler[BSONObjectID]
    val bsonObjectId = BSONObjectID.generate

    "write correctly" in {
      val model = MultilangModel[Option[BSONObjectID]]("en", Some(bsonObjectId))
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument(Seq("lang" -> BSONString("en"), "value" -> bsonObjectId))
    }

    "read correctly" in {
      val bson  = BSONDocument(Seq("lang" -> BSONString("en"), "value" -> bsonObjectId))
      val model = handler.readOpt(bson).get
      model === MultilangModel[Option[BSONObjectID]]("en", Some(bsonObjectId))
    }
  }

  "Multilang handler with Option[String]" should {
    val handler = BSONHandlers.optionalMultilangHandler[String]

    "write correctly with Some[String] type" in {
      val model = MultilangModel[Option[String]]("en", Some("Test"))
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument("lang" -> "en", "value" -> "Test")
    }

    "read correctly with Some[String] type" in {
      val bson  = BSONDocument("lang" -> "en", "value" -> "Test")
      val model = handler.readOpt(bson).get
      model === MultilangModel[Option[String]]("en", Some("Test"))
    }

    "write correctly with None type" in {
      val model = MultilangModel[Option[String]]("en", None)
      val bson  = handler.writeOpt(model).get
      bson === BSONDocument("lang" -> "en")
    }

    "read correctly with None type" in {
      val bson  = BSONDocument("lang" -> "en")
      val model = handler.readOpt(bson).get
      model === MultilangModel[Option[String]]("en", None)
    }
  }

}

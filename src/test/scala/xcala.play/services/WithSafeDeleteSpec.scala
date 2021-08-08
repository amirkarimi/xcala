package xcala.play.services

import org.specs2.mutable.Specification
import play.api.Configuration
import reactivemongo.api.bson.{BSONDocument, BSONObjectID, Macros}
import reactivemongo.api.bson.Macros.Annotations.Key

import scala.concurrent.ExecutionContext
import scala.concurrent.ExecutionContext.Implicits._
import scala.xcala.play.services.WithTestDb

class WithSafeDeleteSpec extends Specification {
  import WithSafeDeleteSpecHelpers._
  import scala.xcala.play.helpers.FutureHelpers._

  "Service with WithSafeDelete" should {
    "allow delete when no related data found for remove by id" >> new WithTestDb {
      val personService = new PersonService()
      val cardService = new CardService()
      val person = Person(name = "test", age = 10)
      personService.insert(person).awaitResult
      val card = Card(title = "test", personId = BSONObjectID.generate)
      cardService.insert(card).awaitResult

      personService.remove(person.id).awaitReady

      personService.findById(person.id).awaitResult must beNone
    }

    "allow delete when no related data found for remove by query" >> new WithTestDb {
      val personService = new PersonService()
      val cardService = new CardService()
      val person1 = Person(name = "test", age = 10)
      personService.insert(person1).awaitResult
      val person2 = Person(name = "test2", age = 12)
      personService.insert(person2).awaitResult
      val card = Card(title = "test", personId = person2.id)
      cardService.insert(card).awaitResult

      personService.remove(BSONDocument("name" -> "test")).awaitResult

      personService.findById(person1.id).awaitResult must beNone
      personService.findById(person2.id).awaitResult must beSome(person2)
    }

    "not allow delete when there is related data for remove by id" >> new WithTestDb {
      val personService = new PersonService()
      val cardService = new CardService()
      val person = Person(name = "test", age = 10)
      personService.insert(person).awaitResult
      val card = Card(title = "test", personId = person.id)
      cardService.insert(card).awaitResult

      personService.remove(person.id).awaitResult must throwA[DeleteConstraintException]
      personService.findById(person.id).awaitResult must beSome(person)
    }

    "not allow delete when there is related data for remove by query" >> new WithTestDb {
      val personService = new PersonService()
      val cardService = new CardService()
      val person1 = Person(name = "test", age = 10)
      personService.insert(person1).awaitResult
      val person2 = Person(name = "test2", age = 12)
      personService.insert(person2).awaitResult
      val card = Card(title = "test", personId = person1.id)
      cardService.insert(card).awaitResult

      personService.remove(BSONDocument("name" -> "test")).awaitResult must throwA[DeleteConstraintException]
      personService.findById(person1.id).awaitResult must beSome(person1)
      personService.findById(person2.id).awaitResult must beSome(person2)
    }
  }
}

object WithSafeDeleteSpecHelpers {
  case class Person(@Key("_id") id: BSONObjectID = BSONObjectID.generate, name: String, age: Int)
  case class Card(@Key("_id") id: BSONObjectID = BSONObjectID.generate, title: String, personId: BSONObjectID)

  class PersonService(implicit val ec: ExecutionContext, val databaseConfig: DatabaseConfig, val configuration: Configuration) extends DataCrudService[Person] with WithSafeDelete {
    val documentHandler = Macros.handler[Person]
    val collectionName = "persons"

    val checkOnDelete = Seq.apply[(String, (BSONObjectID) => BSONDocument)](
      ("cards", id => BSONDocument("personId" -> id))
    )
  }

  class CardService(implicit val ec: ExecutionContext, val databaseConfig: DatabaseConfig, val configuration: Configuration) extends DataCrudService[Card] {
    val documentHandler = Macros.handler[Card]
    val collectionName = "cards"
  }
}
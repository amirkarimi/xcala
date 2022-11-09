package xcala.play.services

import org.specs2.mutable.Specification
import play.api.Configuration
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.BSONDocumentHandler
import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.bson.Macros
import reactivemongo.api.bson.Macros.Annotations.Key

import scala.concurrent.ExecutionContext

class WithSafeDeleteSpec extends Specification {

  import WithSafeDeleteSpecHelpers._
  import xcala.play.helpers.FutureHelpers._

  "Service with WithSafeDelete" should {
    "allow delete when no related data found for remove by id" >> new WithTestDb {
      val personService  = new PersonService()
      val cardService    = new CardService()
      val person: Person = Person(name = "test", age = 10)
      personService.insert(person).awaitResult
      val card: Card = Card(title = "test", personId = BSONObjectID.generate)
      cardService.insert(card).awaitResult

      personService.remove(person.id).awaitReady

      personService.findById(person.id).awaitResult must beNone
    }

    "allow delete when no related data found for remove by query" >> new WithTestDb {
      val personService   = new PersonService()
      val cardService     = new CardService()
      val person1: Person = Person(name = "test", age = 10)
      personService.insert(person1).awaitResult
      val person2: Person = Person(name = "test2", age = 12)
      personService.insert(person2).awaitResult
      val card: Card = Card(title = "test", personId = person2.id)
      cardService.insert(card).awaitResult

      personService.remove(BSONDocument("name" -> "test")).awaitResult

      personService.findById(person1.id).awaitResult must beNone
      personService.findById(person2.id).awaitResult must beSome(person2)
    }

    "not allow delete when there is related data for remove by id" >> new WithTestDb {
      val personService  = new PersonService()
      val cardService    = new CardService()
      val person: Person = Person(name = "test", age = 10)
      personService.insert(person).awaitResult
      val card: Card = Card(title = "test", personId = person.id)
      cardService.insert(card).awaitResult

      personService.remove(person.id).awaitResult must throwA[DeleteConstraintError]
      personService.findById(person.id).awaitResult must beSome(person)
    }

    "not allow delete when there is related data for remove by query" >> new WithTestDb {
      val personService   = new PersonService()
      val cardService     = new CardService()
      val person1: Person = Person(name = "test", age = 10)
      personService.insert(person1).awaitResult
      val person2: Person = Person(name = "test2", age = 12)
      personService.insert(person2).awaitResult
      val card: Card = Card(title = "test", personId = person1.id)
      cardService.insert(card).awaitResult

      personService.remove(BSONDocument("name" -> "test")).awaitResult must throwA[DeleteConstraintError]
      personService.findById(person1.id).awaitResult must beSome(person1)
      personService.findById(person2.id).awaitResult must beSome(person2)
    }
  }

}

object WithSafeDeleteSpecHelpers {
  final case class Person(@Key("_id") id: BSONObjectID = BSONObjectID.generate, name: String, age: Int)
  final case class Card(@Key("_id") id: BSONObjectID = BSONObjectID.generate, title: String, personId: BSONObjectID)

  class PersonService(implicit
      val ec: ExecutionContext,
      val databaseConfig: DatabaseConfig,
      val configuration: Configuration
  ) extends DataCrudService[Person]
      with WithSafeDelete {
    val documentHandler: BSONDocumentHandler[Person] = Macros.handler[Person]
    val collectionName                               = "persons"

    val checkOnDelete: Seq[(String, BSONObjectID => BSONDocument)] =
      Seq.apply[(String, BSONObjectID => BSONDocument)](
        ("cards", id => BSONDocument("personId" -> id))
      )

  }

  class CardService(implicit
      val ec: ExecutionContext,
      val databaseConfig: DatabaseConfig,
      val configuration: Configuration
  ) extends DataCrudService[Card] {
    val documentHandler: BSONDocumentHandler[Card] = Macros.handler[Card]
    val collectionName                             = "cards"
  }

}

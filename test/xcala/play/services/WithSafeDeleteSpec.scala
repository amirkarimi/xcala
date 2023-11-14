package xcala.play.services

import xcala.play.models.DocumentWithId

import play.api
import play.api.Configuration

import java.io.File
import scala.concurrent.ExecutionContext

import com.typesafe.config.ConfigFactory
import org.specs2.main.CommandLine
import org.specs2.mutable.Specification
import reactivemongo.api.bson.BSONDocument
import reactivemongo.api.bson.BSONDocumentHandler
import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.bson.Macros
import reactivemongo.api.bson.Macros.Annotations.Key

class WithSafeDeleteSpec(cmd: CommandLine) extends Specification {

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

  import WithSafeDeleteSpecHelpers._
  import xcala.play.helpers.FutureHelpers._

  "Service with WithSafeDelete" should {
    "allow delete when no related data found for remove by id" >> new WithTestDb(hostName) {
      val personService = new PersonService()
      val cardService   = new CardService()
      val person: Person = Person(name = "test", age = 10)
      personService.insert(person).awaitResult
      val card  : Card   = Card(title = "test", personId = BSONObjectID.generate())
      cardService.insert(card).awaitResult

      personService.remove(person.id.get).awaitReady()

      personService.findById(person.id.get).awaitResult must beNone
    }

    "allow delete when no related data found for remove by query" >> new WithTestDb(hostName) {
      val personService = new PersonService()
      val cardService   = new CardService()
      val person1: Person = Person(name = "test", age = 10)
      personService.insert(person1).awaitResult
      val person2: Person = Person(name = "test2", age = 12)
      personService.insert(person2).awaitResult
      val card   : Card   = Card(title = "test", personId = person2.id.get)
      cardService.insert(card).awaitResult

      personService.remove(BSONDocument("name" -> "test")).awaitResult

      personService.findById(person1.id.get).awaitResult must beNone
      personService.findById(person2.id.get).awaitResult must beSome(person2)
    }

    "not allow delete when there is related data for remove by id" >> new WithTestDb(hostName) {
      val personService = new PersonService()
      val cardService   = new CardService()
      val person: Person = Person(name = "test", age = 10)
      personService.insert(person).awaitResult
      val card  : Card   = Card(title = "test", personId = person.id.get)
      cardService.insert(card).awaitResult

      personService.remove(person.id.get).awaitResult must throwA[DeleteConstraintError]
      personService.findById(person.id.get).awaitResult must beSome(person)
    }

    "not allow delete when there is related data for remove by query" >> new WithTestDb(hostName) {
      val personService = new PersonService()
      val cardService   = new CardService()
      val person1: Person = Person(name = "test", age = 10)
      personService.insert(person1).awaitResult
      val person2: Person = Person(name = "test2", age = 12)
      personService.insert(person2).awaitResult
      val card   : Card   = Card(title = "test", personId = person1.id.get)
      cardService.insert(card).awaitResult

      personService.remove(BSONDocument("name" -> "test")).awaitResult must throwA[DeleteConstraintError]
      personService.findById(person1.id.get).awaitResult must beSome(person1)
      personService.findById(person2.id.get).awaitResult must beSome(person2)
    }
  }

}

object WithSafeDeleteSpecHelpers {

  final case class Person(
      @Key("_id") id: Option[BSONObjectID] = Some(BSONObjectID.generate()),
      name          : String,
      age           : Int
  ) extends DocumentWithId

  final case class Card(
      @Key("_id") id: Option[BSONObjectID] = Some(BSONObjectID.generate()),
      title         : String,
      personId      : BSONObjectID
  ) extends DocumentWithId

  class PersonService(implicit
      val ec            : ExecutionContext,
      val databaseConfig: DatabaseConfig,
      val configuration : Configuration
  ) extends DataReadSimpleServiceImpl[Person]
      with DataSaveServiceImpl[Person]
      with DataRemoveServiceImpl[Person]
      with WithSafeDelete[Person] {
    val documentHandler: BSONDocumentHandler[Person] = Macros.handler[Person]
    val collectionName = "persons"

    val checkOnDelete: Seq[(String, BSONObjectID => BSONDocument)] =
      Seq.apply[(String, BSONObjectID => BSONDocument)](
        ("cards", id => BSONDocument("personId" -> id))
      )

  }

  class CardService(implicit
      val ec            : ExecutionContext,
      val databaseConfig: DatabaseConfig,
      val configuration : Configuration
  ) extends DataReadSimpleServiceImpl[Card]
      with DataSaveServiceImpl[Card] {
    val documentHandler: BSONDocumentHandler[Card] = Macros.handler[Card]
    val collectionName = "cards"
  }

}

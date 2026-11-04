package xcala.play.services

import xcala.play.helpers.FutureHelpers._

import play.api
import play.api.Configuration

import java.io.File
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Random

import com.typesafe.config.ConfigFactory
import org.joda.time.DateTime
import org.specs2.main.CommandLine
import org.specs2.mutable.Specification
import org.specs2.specification.AfterAll
import reactivemongo.api.bson.BSONObjectID

class TaskLockServiceSpec(cmd: CommandLine) extends Specification with AfterAll {
  sequential

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val configFilePath: String =
    cmd.arguments.find(_.startsWith("-Dtest.config")).map(_.split("=")(1)).getOrElse("./conf/local-test.conf")

  private val hostName: String =
    ConfigFactory
      .parseFile(new File(configFilePath))
      .withFallback(Configuration.load(api.Environment.simple(new File("."), api.Mode.Test)).underlying)
      .resolve()
      .getString("mongodbHost")

  private val databaseConfig = new DefaultDatabaseConfig(
    Configuration("mongodb.uri" -> s"mongodb://$hostName/xcala-test-${Math.abs(Random.nextInt())}"),
    ec
  )

  private val service = new TaskLockService(databaseConfig)

  override def afterAll(): Unit = databaseConfig.databaseFuture.flatMap(_.drop()).awaitReady()

  private def newLabel(): String = s"label-${BSONObjectID.generate().stringify}"

  private val now         = DateTime.now
  private val staleBefore = now.minusHours(1)

  "acquire" should {

    "give a free label to the first caller only" in {
      val label = newLabel()

      service.acquire(label, BSONObjectID.generate(), now, staleBefore).run.awaitResult must_== Right(true)
      service.acquire(label, BSONObjectID.generate(), now, staleBefore).run.awaitResult must_== Right(false)
    }

    "give a label to exactly one of many callers at the same moment" in {
      val label = newLabel()

      val attempts = (1 to 10).map(_ => service.acquire(label, BSONObjectID.generate(), now, staleBefore).run)
      val results  = Future.sequence(attempts).awaitResult

      results.count(_ == Right(true)) must_== 1
    }

    "take over a lock taken before staleBefore" in {
      val label = newLabel()
      service.acquire(label, BSONObjectID.generate(), now.minusHours(2), staleBefore).run.awaitReady()

      service.acquire(label, BSONObjectID.generate(), now, staleBefore).run.awaitResult must_== Right(true)
    }
  }

  "find" should {

    "read a fresh lock as stored, and nothing for a free or stale lock" in {
      val label  = newLabel()
      val holder = BSONObjectID.generate()
      service.find(label, staleBefore).run.awaitResult must_== Right(None)

      service.acquire(label, holder, now, staleBefore).run.awaitReady()
      val stored = service.find(label, staleBefore).run.awaitResult
      stored.map(_.map(lock => (lock.label, lock.taskId, lock.lockedAt.getMillis))) must_==
        Right(Some((label, holder, now.getMillis)))
      service.find(label, staleBefore = now.plusMinutes(1)).run.awaitResult must_== Right(None)
    }
  }

  "isHeld" should {

    "tell whether a fresh lock exists" in {
      val label = newLabel()
      service.isHeld(label, staleBefore).run.awaitResult must_== Right(false)

      service.acquire(label, BSONObjectID.generate(), now, staleBefore).run.awaitReady()
      service.isHeld(label, staleBefore).run.awaitResult must_== Right(true)

      service.isHeld(label, staleBefore = now.plusMinutes(1)).run.awaitResult must_== Right(false)
    }
  }

  "holder" should {

    "name the task that holds a fresh lock, and nobody for a free or stale lock" in {
      val label  = newLabel()
      val holder = BSONObjectID.generate()
      service.holder(label, staleBefore).run.awaitResult must_== Right(None)

      service.acquire(label, holder, now, staleBefore).run.awaitReady()
      service.holder(label, staleBefore).run.awaitResult must_== Right(Some(holder))
      service.holder(label, staleBefore = now.plusMinutes(1)).run.awaitResult must_== Right(None)
    }
  }

  "release" should {

    "free the label for the task that holds it, and only for that task" in {
      val label  = newLabel()
      val holder = BSONObjectID.generate()
      service.acquire(label, holder, now, staleBefore).run.awaitReady()

      service.release(label, BSONObjectID.generate()).run.awaitReady()
      service.isHeld(label, staleBefore).run.awaitResult must_== Right(true)

      service.release(label, holder).run.awaitReady()
      service.isHeld(label, staleBefore).run.awaitResult must_== Right(false)
    }
  }

}

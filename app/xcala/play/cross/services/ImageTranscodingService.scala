package xcala.play.cross.services

import xcala.play.cross.models._
import xcala.play.cross.services.ImageTranscodingService.ResizeResultListenerActor.HandleIncomingResult
import xcala.play.cross.services.s3.FileStorageService

import akka.Done
import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.kafka.CommitterSettings
import akka.kafka.ConsumerMessage
import akka.kafka.ConsumerSettings
import akka.kafka.ProducerMessage
import akka.kafka.ProducerSettings
import akka.kafka.Subscriptions
import akka.kafka.scaladsl._
import akka.pattern.ask
import akka.pattern.pipe
import akka.stream.AbruptStageTerminationException
import akka.stream.AbruptTerminationException
import akka.stream.Materializer
import akka.stream.RestartSettings
import akka.stream.scaladsl.RestartSource
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.RequestHeader

import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.config.Config
import io.sentry.Sentry
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import reactivemongo.api.bson.BSONObjectID

/*
  This class needs to be manually instantiated using its provider because not all Xcala based projects need it.
  DO NOT ADD SINGLETON
 */
class ImageTranscodingService(
    actorSystem       : ActorSystem,
    configuration     : Configuration,
    fileStorageService: FileStorageService
)(
    implicit
    val ec              : ExecutionContext,
    val mat             : Materializer,
    val imageRenderSizes: Set[ImageResizedRenderType]
) {
  import ImageTranscodingService._

  private val bootstrapServer: String = configuration.get[String]("kafka.bootstrapServer")
  private val requestTopic   : String = "image-transcoding-requests"

  private lazy val consumerConfigs: Config = configuration.underlying.getConfig("akka.kafka.consumer")
  private lazy val producerConfigs: Config = configuration.underlying.getConfig("akka.kafka.producer")

  private lazy val producerSettings: ProducerSettings[String, String] =
    ProducerSettings(
      config          = producerConfigs,
      keySerializer   = new StringSerializer,
      valueSerializer = new StringSerializer
    )
      .withBootstrapServers(bootstrapServer)

  private val groupId: String = "resizing-clients-listener"

  private val consumerSettings: ConsumerSettings[String, String] =
    ConsumerSettings(
      config            = consumerConfigs,
      keyDeserializer   = new StringDeserializer,
      valueDeserializer = new StringDeserializer
    )
      .withBootstrapServers(bootstrapServer)
      .withGroupId(groupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  private val clientResultTopic: String = UUID.randomUUID().toString

  private val resizeResultListenerActor: ActorRef =
    ResizeResultListenerActor.create(
      actorSystem
    )

  private lazy val producerSink: Sink[ProducerRecord[String, String], Future[Done]] =
    Producer.plainSink(producerSettings)

  implicit val timeout: Timeout = Timeout(10.minutes)

  private val committerSettings: CommitterSettings =
    CommitterSettings(actorSystem)

  private val committerSink: Sink[ConsumerMessage.Committable, Future[Done]] =
    Committer.sink(committerSettings)

  private val control: AtomicReference[Consumer.Control] =
    new AtomicReference[Consumer.Control](Consumer.NoopControl)

  lazy val restartSettings: RestartSettings = RestartSettings(
    minBackoff   = 30.seconds,
    maxBackoff   = 4.minutes,
    randomFactor = 0.2
  ).withMaxRestarts(
    count  = Int.MaxValue,
    within = 1.minutes
  )

  val streamCompletion: Future[Done] =
    RestartSource
      .onFailuresWithBackoff(restartSettings) { () =>
        Consumer
          .committableSource(
            consumerSettings,
            Subscriptions.topics(clientResultTopic)
          )
          .map { message =>
            {
              Try(
                Json.fromJson[ImageTranscodingResponse](
                  Json.parse(message.record.value())
                ).asOpt
              )
                .toOption
                .flatten
            } match {
              case None =>
                Sentry.captureMessage("Invalid incoming json message from kafka")
                ProducerMessage.passThrough[
                  String,
                  String,
                  ConsumerMessage.CommittableOffset
                ](
                  message.committableOffset
                )
              case Some(response) =>
                resizeResultListenerActor ! HandleIncomingResult(response)
                ProducerMessage.passThrough[
                  String,
                  String,
                  ConsumerMessage.CommittableOffset
                ](
                  message.committableOffset
                )

            }

          }
          .via(Producer.flexiFlow(producerSettings))
          .map(_.passThrough)
          .mapMaterializedValue(c => control.set(c))
          .recoverWith {
            case _: AbruptStageTerminationException | _: AbruptTerminationException =>
              Source.empty
            case e: Throwable =>
              Sentry.captureException(e)
              throw e
          }
      }
      .runWith(committerSink)
      .recoverWith {
        case _: AbruptStageTerminationException | _: AbruptTerminationException =>
          Future.successful(Done)

        case e: Throwable =>
          Sentry.captureMessage(
            s"Too many failed retries has stopped a $groupId consumer with a ${e.getClass.getSimpleName}"
          )
          Future.failed(e)
      }

  control.get().drainAndShutdown(streamCompletion)

  @SuppressWarnings(Array("SwallowedException"))
  def uploadPreResizesByFileId[Id](
      fileId: Id
  )(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Unit]] = {
    val fileIdString: String =
      fileId match {
        case x: BSONObjectID => x.stringify
        case x => x.toString
      }
    fileStorageService.findByObjectName(
      fileIdString
    )
      .flatMap { file =>
        val request: ImageTranscodingRequest =
          ImageTranscodingRequest.create(
            objectName = fileIdString,
            bucketName = fileStorageService.bucketName,
            targetWidthToResizedImageNames =
              imageRenderSizes
                .map { imageRenderSize =>
                  imageRenderSize.overriddenWidth -> imageRenderSize.resizedObjectName(fileIdString)
                }
                .toMap,
            fileOriginalName = file.originalName,
            resultTopic      = clientResultTopic
          )

        val requestInJson: String =
          Json.toJsObject(request).toString()

        val askingForAnswer: Future[Either[String, Unit]] =
          (resizeResultListenerActor ? ResizeResultListenerActor.GetResult(request.id))
            .mapTo[ImageTranscodingResponse]
            .map {
              case ImageTranscodingResponse(_, error) if error.isEmpty =>
                Right(())
              case ImageTranscodingResponse(_, error) =>
                Left(error.mkString)

            }

        Source.single(requestInJson)
          .map { json =>
            new ProducerRecord[String, String](requestTopic, json)
          }
          .runWith(producerSink)

        askingForAnswer
          .map { askingResult =>
            try {
              file.content.close()
            } catch {
              case _: Throwable => ()
              /* do nothing */
            }
            askingResult
          }
          .recoverWith {
            case e: Throwable =>
              try {
                file.content.close()
              } catch {
                case _: Throwable => ()
                /* do nothing */
              }
              Future.failed(e)
          }

      }
  }

  def uploadPreResizes[Id](preResizedImageHolder: PreResizedImageHolder[Id])(implicit
      requestHeader: RequestHeader
  ): Future[Either[String, Unit]] =
    preResizedImageHolder.maybeImageFileId match {
      case Some(fileId) =>
        uploadPreResizesByFileId(
          fileId = fileId
        ).transformWith {
          case Failure(exception) =>
            Sentry.captureException(exception)
            Future.failed(exception)

          case Success(either) =>
            Future.successful(either)

        }

      case _ => Future.successful(Right(()))
    }

  def removePreResizes[Id](
      preResizedImageHolder: PreResizedImageHolder[Id]
  )(
      implicit
      fileStorageService: FileStorageService,
      ec                : ExecutionContext,
      requestHeader     : RequestHeader
  ): Future[_] = {
    preResizedImageHolder.maybeImageFileId match {
      case Some(imageFileId) =>
        Future.traverse(imageRenderSizes) { allowedResize =>
          val resizedFileName: String = allowedResize.resizedObjectName(
            imageFileId match {
              case x: BSONObjectID => x.stringify
              case x => x.toString
            }
          )
          fileStorageService.deleteByObjectName(resizedFileName).transformWith {
            case Failure(e) =>
              Future.failed(e)

            case Success(_) =>
              Future.successful(())
          }
        }
      case _ =>
        Future.successful(())
    }

  }

}

object ImageTranscodingService {

  final class ResizeResultListenerActor extends Actor {

    import ResizeResultListenerActor._
    import context.dispatcher

    override def receive: Receive = receive(Map.empty)

    def receive(trackingIds: Map[UUID, Promise[ImageTranscodingResponse]]): Receive = {
      case GetResult(uuid) =>
        val promise     = Promise[ImageTranscodingResponse]()
        val senderActor = sender()
        promise.future.pipeTo(senderActor)
        context.become(receive(trackingIds + (uuid -> promise)))

      case HandleIncomingResult(imageTranscodingResponse) =>
        trackingIds.get(imageTranscodingResponse.id) match {
          case None =>
            Sentry.captureMessage(
              "trackingId does not exists!"
            )
          case Some(promise) =>
            promise.success(imageTranscodingResponse)
            context.become(receive(trackingIds.removed(imageTranscodingResponse.id)))
        }
    }

  }

  object ResizeResultListenerActor {

    final case class GetResult(id: UUID)
    final case class HandleIncomingResult(ImageTranscodingResponse: ImageTranscodingResponse)

    def create(actorSystem: ActorSystem): ActorRef =
      actorSystem.actorOf(
        Props.create(
          classOf[ResizeResultListenerActor]
        )
      )

  }

}

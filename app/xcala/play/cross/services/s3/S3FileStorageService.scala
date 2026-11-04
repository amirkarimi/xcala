package xcala.play.cross.services.s3

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, CopyObjectResponse}
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import xcala.play.cross.services.s3.FileStorageService.FileS3Object
import xcala.play.models.SentryExtendedBase

import akka.NotUsed
import akka.http.scaladsl.model.ContentType
import akka.http.scaladsl.model.ContentTypes
import akka.stream.Materializer
import akka.stream.alpakka.s3.MetaHeaders
import akka.stream.alpakka.s3.MultipartUploadResult
import akka.stream.alpakka.s3.ObjectMetadata
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamConverters
import akka.util.ByteString
import play.api.mvc.RequestHeader

import java.{util => ju}
import java.io.InputStream
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success

import io.sentry.Hint

class S3FileStorageService @Inject() (
    val config    : play.api.Configuration,
    sentryExtended: SentryExtendedBase
)(implicit val ec: ExecutionContext, materializer: Materializer) extends FileStorageService {
  lazy val accessKeyId     = config.get[String]("alpakka.s3.aws.credentials.access-key-id")
  lazy val secretAccessKey = config.get[String]("alpakka.s3.aws.credentials.secret-access-key")
  lazy val endpointUrl     = config.get[String]("alpakka.s3.endpoint-url")

  private val credentialsProvider = StaticCredentialsProvider.create(
    AwsBasicCredentials.create(accessKeyId, secretAccessKey)
  )

  override def upload(
      objectName  : String,
      content     : Array[Byte],
      contentType : String,
      originalName: String,
      path        : Option[String]
  )(implicit
      requestHeader: RequestHeader
  ): Future[Boolean] = {
    val cleanPath: String = getCleanPath(path)

    val sink: Sink[ByteString, Future[MultipartUploadResult]] = S3
      .multipartUpload(
        bucket = bucketName,
        key    = cleanPath.concat(objectName),
        metaHeaders = MetaHeaders(
          Map(
            "name" -> java.net.URLEncoder.encode(originalName, StandardCharsets.UTF_8.name())
          )
        ),
        contentType =
          ContentType.parse(contentType).toOption.getOrElse(ContentTypes.`application/octet-stream`)
      )

    Source.single(ByteString(content))
      .runWith(sink)
      .transformWith {
        case Success(_) => Future.successful(true)
        case Failure(e) =>
          val hint = new Hint
          hint.set("objectName", objectName)
          sentryExtended.captureExceptionWithHint(e, hint)
          Future.successful(false)
      }

  }

  override def findByObjectName(objectName: String, path: Option[String])(implicit
      requestHeader: RequestHeader
  ): Future[FileS3Object] = {

    val cleanPath: String = getCleanPath(path)

    val s3Source: Source[ByteString, Future[ObjectMetadata]] = S3.getObject(
      bucket = bucketName,
      key    = cleanPath.concat(objectName)
    )

    val (metadataFuture: Future[ObjectMetadata], inputStream: InputStream) =
      s3Source.toMat(StreamConverters.asInputStream())(Keep.both).run()

    for {
      metadata   <- metadataFuture
      contentType = metadata.contentType
      originalName =
        metadata.metadata.find(_.name() == "X-Amz-Meta-Name").map(_.value()).getOrElse(objectName)
      contentLength = metadata.contentLength

    } yield FileS3Object(
      objectName    = objectName,
      originalName  = URLDecoder.decode(originalName, StandardCharsets.UTF_8.name()),
      content       = inputStream,
      contentLength = Some(contentLength).filter(_ != 0),
      contentType   = contentType,
      path          = path
    )

  }.transformWith {
    case Success(value) =>
      Future.successful(value)
    case Failure(e) =>
      val hint = new Hint
      hint.set("objectName", objectName)
      sentryExtended.captureExceptionWithHint(e, hint)
      Future.failed(e)
  }

  override def deleteByObjectName(objectName: String, path: Option[String])(implicit
      requestHeader: RequestHeader
  ): Future[Boolean] =
    S3.deleteObject(
      bucket = bucketName,
      key    = objectName
    )
      .run()
      .transformWith {
        case Success(_) => Future.successful(true)
        case Failure(e) =>
          val hint = new Hint
          hint.set("objectName", objectName)
          sentryExtended.captureExceptionWithHint(e, hint)
          Future.successful(false)
      }

  override def filesStream(path: Option[String]): Source[String, NotUsed] =
    S3
      .listBucket(
        bucket = bucketName,
        prefix = None
      )
      .map(_.key)

  def updateObjectMetadata(objectName: String)(
      conditionFunction   : ju.Map[String, String] => Boolean,
      userMetadataFunction: ju.Map[String, String] => ju.Map[String, String],
      contentTypeFunction : String => String
  ): Future[Option[CopyObjectResponse]] =
    Future {
      val s3Client: S3Client =
        S3Client.builder()
          .region(Region.EU_CENTRAL_1)
          .credentialsProvider(credentialsProvider)
          .endpointOverride(URI.create(endpointUrl))
          .serviceConfiguration(
            S3Configuration.builder().pathStyleAccessEnabled(true).build()
          )
          .build()

      val headRequest: HeadObjectRequest =
        HeadObjectRequest.builder()
          .bucket(bucketName)
          .key(objectName)
          .build()

      val headResponse: HeadObjectResponse =
        s3Client.headObject(headRequest)

      val metadataMap = new ju.HashMap(headResponse.metadata())
      if (conditionFunction(metadataMap)) {

        val userMetadata: ju.Map[String, String] = userMetadataFunction(metadataMap)
        val contentType : String                 = contentTypeFunction(headResponse.contentType())

        Some(s3Client
          .copyObject(
            CopyObjectRequest.builder()
              .sourceBucket(bucketName)
              .sourceKey(objectName)
              .destinationBucket(bucketName)
              .destinationKey(objectName)
              .metadata(userMetadata)                          // Set user metadata
              .contentType(contentType)                        // Preserve content type
              .contentEncoding(headResponse.contentEncoding()) // Preserve content encoding
              .cacheControl(headResponse.cacheControl())       // Preserve cache control
              .metadataDirective("REPLACE")
              .build()
          ))
      } else {
        None
      }
    }

  override def getList(path: Option[String])(implicit requestHeader: RequestHeader): Future[List[String]] =
    filesStream(path)
      .toMat(Sink.seq)(Keep.right)
      .run()
      .map(_.toList)
      .recoverWith { case e: Throwable =>
        sentryExtended.captureException(e)
        Future.successful(List.empty[String])
      }

}

package xcala.play.cross.services.s3

import xcala.play.models.SentryExtendedBase

import akka.NotUsed
import akka.stream.scaladsl.Source
import play.api.mvc.RequestHeader

import java.io.File
import java.io.FileOutputStream
import java.util
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.jdk.CollectionConverters._
import scala.jdk.FutureConverters._
import scala.util.Failure
import scala.util.Success

import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioAsyncClient
import io.minio.RemoveObjectArgs
import io.minio.UploadObjectArgs
import io.sentry.Hint
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol

class MinioFileStorageService @Inject() (
    val config    : play.api.Configuration,
    sentryExtended: SentryExtendedBase
)(implicit val ec: ExecutionContext) extends FileStorageService {
  import FileStorageService._

  private lazy val baseURL: String = config.get[String]("fileStorage.s3.baseUrl")

  def upload(
      objectName  : String,
      content     : Array[Byte],
      contentType : String,
      originalName: String,
      path        : Option[String] = None
  )(implicit
      requestHeader: RequestHeader
  ): Future[Boolean] = {
    val userMetaData = Map("name" -> originalName).asJava
    val cleanPath    = getCleanPath(path)
    val f            = File.createTempFile(objectName, "")
    val fos          = new FileOutputStream(f)
    fos.write(content)
    fos.close()

    getClient
      .uploadObject(
        UploadObjectArgs.builder
          .bucket(defaultBucketName)
          .`object`(cleanPath.concat(objectName))
          .filename(f.getAbsolutePath)
          .userMetadata(userMetaData)
          .contentType(contentType)
          .build
      )
      .asScala

  }.transformWith {
    case Success(_) => Future.successful(true)
    case Failure(e) =>
      val hint = new Hint
      hint.set("objectName", objectName)
      sentryExtended.captureExceptionWithHint(e, hint)
      Future.successful(false)

  }

  def findByObjectName(objectName: String, path: Option[String] = None)(implicit
      requestHeader: RequestHeader
  ): Future[FileS3Object] = {
    val cleanPath = getCleanPath(path)

    for {
      objectResponse <- getClient
        .getObject(
          GetObjectArgs.builder
            .bucket(defaultBucketName)
            .`object`(cleanPath.concat(objectName))
            .build
        )
        .asScala

      contentType   = Option(objectResponse.headers().get("Content-Type"))
      originalName  = Option(objectResponse.headers().get("x-amz-meta-name")).getOrElse(objectName)
      contentLength = Option(objectResponse.headers().get("Content-Length")).map(_.toLong)

    } yield FileS3Object(
      objectName    = objectName,
      originalName  = originalName,
      content       = objectResponse,
      contentType   = contentType,
      contentLength = contentLength,
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

  def deleteByObjectName(objectName: String, path: Option[String] = None)(implicit
      requestHeader: RequestHeader
  ): Future[Boolean] = {
    val cleanPath = getCleanPath(path)
    getClient
      .removeObject(
        RemoveObjectArgs
          .builder()
          .bucket(defaultBucketName)
          .`object`(cleanPath.concat(objectName))
          .build()
      )
      .asScala
  }.transformWith {
    case Success(_) => Future.successful(true)
    case Failure(e) =>
      val hint = new Hint
      hint.set("objectName", objectName)
      sentryExtended.captureExceptionWithHint(e, hint)
      Future.successful(false)
  }

  /** Return all files name in the given path
    *
    * @param path
    *   String, Ex: folder1/folder2/
    * @return
    */
  def getList(path: Option[String] = None)(implicit
      requestHeader: RequestHeader
  ): Future[List[String]] =
    Future {
      val cleanPath = getCleanPath(path)
      val res = getClient
        .listObjects(
          ListObjectsArgs
            .builder()
            .bucket(defaultBucketName)
            .prefix(cleanPath)
            .recursive(true)
            .build()
        )
        .asScala

      res
        .map(x => Option(x.get()))
        .collect { case Some(item) =>
          item.objectName().replace(cleanPath, "")
        }
        .toList
    }.recoverWith { case e: Throwable =>
      sentryExtended.captureException(e)
      Future.successful(List.empty[String])
    }

  def filesStream(path: Option[String] = None): Source[String, NotUsed] = {
    val cleanPath = getCleanPath(path)

    Source.fromIterator { () =>
      getClient
        .listObjects(
          ListObjectsArgs
            .builder()
            .bucket(defaultBucketName)
            .prefix(cleanPath)
            .recursive(true)
            .build()
        )
        .asScala
        .map(x => Option(x.get()))
        .collect { case Some(item) =>
          item.objectName().replace(cleanPath, "")
        }
        .iterator
    }

  }

  /** It makes the default bucket if it doesn't exists
    *
    * @return
    */
  def createDefaultBucket()(implicit requestHeader: RequestHeader): Future[Boolean] = {
    for {
      found <- getClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucketName).build()).asScala
      _ <- {
        if (!found) {
          getClient.makeBucket(MakeBucketArgs.builder.bucket(defaultBucketName).build).asScala
        } else {
          Future.successful(())
        }
      }

    } yield true
  }.recover { case e: Throwable =>
    sentryExtended.captureException(e)
    false
  }

  val okHttpClient: OkHttpClient = new OkHttpClient()
    .newBuilder()
    .connectTimeout(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)
    .writeTimeout(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)
    .readTimeout(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)
    .connectionPool(new ConnectionPool(10, 15, TimeUnit.SECONDS))
    .protocols(util.Arrays.asList(Protocol.HTTP_1_1))
    .build()

  /** Make S3 client object
    *
    * @return
    */
  private def getClient: MinioAsyncClient = {
    val accessKey = config.get[String]("fileStorage.s3.accessKey")
    val secretKey = config.get[String]("fileStorage.s3.secretKey")
    MinioAsyncClient.builder
      .endpoint(baseURL)
      .httpClient(okHttpClient)
      .credentials(accessKey, secretKey)
      .build
  }

  /** All operations are based on this bucket name
    *
    * @return
    */
  private def defaultBucketName: String =
    bucketName

}

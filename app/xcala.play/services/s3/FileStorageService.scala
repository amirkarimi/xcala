package xcala.play.services.s3

import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol

import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Arrays
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
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
import io.sentry.Sentry

object FileStorageService {

  final case class FileS3Object(
      objectName: String,
      originalName: String,
      content: InputStream,
      contentType: Option[String],
      contentLength: Option[Long],
      path: Option[String]
  )

}

@Singleton
class FileStorageService @Inject() (
    config: play.api.Configuration
)(implicit val ec: ExecutionContext) {

  import FileStorageService._

  private lazy val baseURL: String    = config.get[String]("fileStorage.s3.baseUrl")
  private lazy val bucketName: String = config.get[String]("fileStorage.s3.bucketName")

  lazy val fileStorageUrl: String = s"$baseURL/$bucketName"

  /** Upload file to default bucket and the given path
    *
    * @param objectName
    *   String, File stored with this name in s3 and in our case it is UUID
    * @param content
    *   String, File content
    * @param contentType
    *   String
    * @param originalName
    *   String, extra info
    * @param path
    *   String, Path of s3, Ex: folder1/folder2/folder3/
    * @return
    */
  def upload(
      objectName: String,
      content: Array[Byte],
      contentType: String,
      originalName: String,
      path: Option[String] = None
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
      Sentry.captureException(e, hint)
      Future.successful(false)

  }

  /** Read file from s3
    *
    * @param objectName
    *   String, name of file in s3. Ex: picture.jpg
    * @param path
    *   String, directory of the file. Ex: first/second/thirdFolder/
    * @return
    */
  def findByObjectName(objectName: String, path: Option[String] = None): Future[FileS3Object] = {
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
      objectName = objectName,
      originalName = originalName,
      content = objectResponse,
      contentType = contentType,
      contentLength = contentLength,
      path = path
    )

  }.transformWith {
    case Success(value) =>
      Future.successful(value)
    case Failure(e) =>
      val hint = new Hint
      hint.set("objectName", objectName)
      Sentry.captureException(e, hint)
      Future.failed(e)
  }

  /** Delete file by name and path
    *
    * @param objectName
    *   String, file name, in our case it is fileEntity UUID
    * @param path
    *   String Ex: folder1/folder2/
    * @return
    */
  def deleteByObjectName(objectName: String, path: Option[String] = None): Future[Boolean] = {
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
      Sentry.captureException(e, hint)
      Future.successful(false)
  }

  /** Return all files name in the given path
    *
    * @param path
    *   String, Ex: folder1/folder2/
    * @return
    */
  def getList(path: Option[String] = None): Future[List[String]] =
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
    }.recoverWith { case (e: Throwable) =>
      Sentry.captureException(e)
      Future.successful(List.empty[String])
    }

  /** It makes the default bucket if it doesn't exists
    *
    * @return
    */
  def createDefaultBucket(): Future[Boolean] = {
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
    Sentry.captureException(e)
    false
  }

  val okHttpClient = new OkHttpClient()
    .newBuilder()
    .connectTimeout(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)
    .writeTimeout(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)
    .readTimeout(TimeUnit.SECONDS.toMillis(60), TimeUnit.MILLISECONDS)
    .connectionPool(new ConnectionPool(10, 15, TimeUnit.SECONDS))
    .protocols(Arrays.asList(Protocol.HTTP_1_1))
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

  /** Clean deformed path Ex: Some("folder1/") => folder1/ Some("folder1") => folder1/ Some("") => "" None => ""
    *
    * @param path
    *   Option[String]
    * @return
    */
  private def getCleanPath(path: Option[String]): String = {
    path match {
      case Some(s) if s.nonEmpty && s.last == '/' => s
      case Some(s) if s.nonEmpty                  => s.concat("/")
      case _                                      => ""
    }
  }

}

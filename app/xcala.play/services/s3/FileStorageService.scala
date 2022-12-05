package xcala.play.services.s3

import java.io.File
import java.io.FileOutputStream
import java.lang
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.ListObjectsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.RemoveObjectArgs
import io.minio.Result
import io.minio.UploadObjectArgs
import io.minio.messages.Item

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import io.sentry.Sentry
import io.sentry.Hint
import akka.stream.scaladsl.StreamConverters
import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import akka.stream.scaladsl.Flow
import akka.actor.ActorSystem
import scala.concurrent.duration._
import javax.inject.Singleton
import javax.inject.Inject

object FileStorageService {

  final case class FileS3Object(
      objectName: String,
      originalName: String,
      content: Source[ByteString, Future[IOResult]],
      contentType: Option[String],
      contentLength: Option[Long],
      path: Option[String]
  )

}

@Singleton
class FileStorageService @Inject() (
    config: play.api.Configuration,
    actorSystem: ActorSystem
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
  ): Future[Boolean] = Future {
    Try {

      val userMetaData = mapAsJavaMap(Map("name" -> originalName))
      val cleanPath    = getCleanPath(path)
      val f            = File.createTempFile(objectName, "")
      val fos          = new FileOutputStream(f)
      fos.write(content)

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

    } match {
      case Success(_) => true
      case Failure(e) =>
        val hint = new Hint
        hint.set("objectName", objectName)
        Sentry.captureException(e, hint)
        false
    }
  }

  /** Read file from s3
    *
    * @param objectName
    *   String, name of file in s3. Ex: picture.jpg
    * @param path
    *   String, directory of the file. Ex: first/second/thirdFolder/
    * @return
    */
  def findByObjectName(objectName: String, path: Option[String] = None): Future[Option[FileS3Object]] =
    Future {
      Try {

        val cleanPath = getCleanPath(path)
        val stream = getClient.getObject(
          GetObjectArgs.builder
            .bucket(defaultBucketName)
            .`object`(cleanPath.concat(objectName))
            .build
        )

        val contentType   = Option(stream.headers().get("Content-Type"))
        val originalName  = Option(stream.headers().get("x-amz-meta-name")).getOrElse(objectName)
        val contentLength = Option(stream.headers().get("Content-Length")).map(_.toLong)

        val res: Source[ByteString, Future[IOResult]] =
          StreamConverters.fromInputStream(() => stream)
        // Just to make sure stream will be closed even if the file download is cancelled or not finished for any reason
        // Please note that this schedule will be cancelled once the stream is completed.
        val cancellable = actorSystem.scheduler.scheduleOnce(4.minutes) {
          try {
            stream.close()
          } catch {
            case e: Throwable => Sentry.captureException(e)
          }
        }

        // Add an extra stage for doing cleanups and cancelling the scheduled task
        val lazyFlow = Flow[ByteString]
          .concatLazy(Source.lazyFuture { () =>
            Future {
              stream.close()
              cancellable.cancel()
              ByteString.empty
            }
          })

        val withCleanupRes = res.via(lazyFlow).recover { case _: Throwable =>
          stream.close()
          ByteString.empty
        }

        FileS3Object(
          objectName = objectName,
          originalName = originalName,
          content = withCleanupRes,
          contentType = contentType,
          contentLength = contentLength,
          path = path,
        )

      } match {
        case Success(f) => Some(f)
        case Failure(e) =>
          val hint = new Hint
          hint.set("objectName", objectName)
          Sentry.captureException(e, hint)
          None
      }
    }

  /** Delete file by name and path
    *
    * @param objectName
    *   String, file name, in our case it is fileEntity UUID
    * @param path
    *   String Ex: folder1/folder2/
    * @return
    */
  def deleteByObjectName(objectName: String, path: Option[String] = None): Future[Boolean] =
    Future {
      Try {
        val cleanPath = getCleanPath(path)
        getClient.removeObject(
          RemoveObjectArgs
            .builder()
            .bucket(defaultBucketName)
            .`object`(cleanPath.concat(objectName))
            .build()
        )
      } match {
        case Success(_) => true
        case Failure(e) =>
          val hint = new Hint
          hint.set("objectName", objectName)
          Sentry.captureException(e, hint)
          false
      }
    }

  /** Return all files name in the given path
    *
    * @param path
    *   String, Ex: folder1/folder2/
    * @return
    */
  def getList(path: Option[String] = None): Future[List[String]] = Future {
    Try {
      val cleanPath = getCleanPath(path)
      val res: lang.Iterable[Result[Item]] = getClient.listObjects(
        ListObjectsArgs
          .builder()
          .bucket(defaultBucketName)
          .prefix(cleanPath)
          .recursive(true)
          .build()
      )

      res
        .iterator()
        .asScala
        .toList
        .map(x => Option(x.get()))
        .collect { case Some(item) =>
          item.objectName().replace(cleanPath, "")
        }
    } match {
      case Success(res) => res
      case Failure(e) =>
        Sentry.captureException(e)
        List.empty[String]
    }
  }

  /** It makes the default bucket if it doesn't exists
    *
    * @return
    */
  def createDefaultBucket(): Future[Boolean] = Future {
    Try {
      val found =
        getClient.bucketExists(BucketExistsArgs.builder().bucket(defaultBucketName).build())

      if (!found) {
        getClient.makeBucket(MakeBucketArgs.builder.bucket(defaultBucketName).build)
      }
    } match {
      case Success(_) => true
      case Failure(e) =>
        Sentry.captureException(e)
        false
    }

  }

  /** Make S3 client object
    *
    * @return
    */
  private def getClient: MinioClient = {
    val accessKey = config.get[String]("fileStorage.s3.accessKey")
    val secretKey = config.get[String]("fileStorage.s3.secretKey")
    MinioClient.builder
      .endpoint(baseURL)
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

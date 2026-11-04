package xcala.play.cross.extensions

import akka.stream.scaladsl.Source
import akka.util.ByteString
import play.api.libs.ws.BodyWritable
import play.api.libs.ws.WSAuthScheme
import play.api.libs.ws.WSBody
import play.api.libs.ws.WSCookie
import play.api.libs.ws.WSProxyServer
import play.api.libs.ws.WSRequest
import play.api.libs.ws.WSRequestFilter
import play.api.libs.ws.WSResponse
import play.api.libs.ws.WSSignatureCalculator
import play.api.mvc.MultipartFormData

import java.io.File
import java.lang.StackWalker.StackFrame
import java.net.URI
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.util.Failure
import scala.util.Success

import io.prometheus.client.Collector
import io.prometheus.client.Histogram

object MonitoredWSRequest {

  implicit class RequestExtended(request: WSRequest)(implicit ec: ExecutionContext) extends WSRequest {

    override def url: String =
      request.url

    override def uri: URI =
      request.uri

    override def contentType: Option[String] =
      request.contentType

    override def cookies: Seq[WSCookie] =
      request.cookies

    override def withDisableUrlEncoding(disableUrlEncoding: Boolean): MonitoredWSRequest.RequestExtended =
      request.withDisableUrlEncoding(disableUrlEncoding)

    override def withUrl(url: String): MonitoredWSRequest.RequestExtended =
      request.withUrl(url)

    override def stream(): Future[WSResponse] =
      request.stream()

    @deprecated("Use withHttpHeaders or addHttpHeaders", "2.6.0")
    override def withHeaders(headers: (String, String)*): MonitoredWSRequest.RequestExtended =
      request.withHeaders(headers: _*)

    override def withHttpHeaders(headers: (String, String)*): MonitoredWSRequest.RequestExtended =
      request.withHttpHeaders(headers: _*)

    override def withQueryString(parameters: (String, String)*): MonitoredWSRequest.RequestExtended =
      request.withQueryStringParameters(parameters: _*)

    override def withQueryStringParameters(parameters: (String, String)*)
        : MonitoredWSRequest.RequestExtended =
      request.withQueryStringParameters(parameters: _*)

    override def withCookies(cookie: WSCookie*): MonitoredWSRequest.RequestExtended =
      request.withCookies(cookie: _*)

    override def method: String =
      request.method

    override def body: WSBody =
      request.body

    override def headers: Map[String, Seq[String]] =
      request.headers

    override def queryString: Map[String, Seq[String]] =
      request.queryString

    override def calc: Option[WSSignatureCalculator] =
      request.calc

    override def auth: Option[(String, String, WSAuthScheme)] =
      request.auth

    override def followRedirects: Option[Boolean] =
      request.followRedirects

    override def requestTimeout: Option[Duration] =
      request.requestTimeout

    override def virtualHost: Option[String] =
      request.virtualHost

    override def proxyServer: Option[WSProxyServer] =
      request.proxyServer

    override def sign(calc: WSSignatureCalculator): MonitoredWSRequest.RequestExtended =
      request.sign(calc)

    override def withAuth(
        username: String,
        password: String,
        scheme  : WSAuthScheme
    ): MonitoredWSRequest.RequestExtended =
      request.withAuth(username = username, password = password, scheme = scheme)

    override def withFollowRedirects(follow: Boolean): MonitoredWSRequest.RequestExtended =
      request.withFollowRedirects(follow)

    override def withRequestTimeout(timeout: Duration): MonitoredWSRequest.RequestExtended =
      request.withRequestTimeout(timeout)

    override def withRequestFilter(filter: WSRequestFilter): MonitoredWSRequest.RequestExtended =
      request.withRequestFilter(filter)

    override def withVirtualHost(vh: String): MonitoredWSRequest.RequestExtended =
      request.withVirtualHost(vh)

    override def withProxyServer(proxyServer: WSProxyServer): MonitoredWSRequest.RequestExtended =
      request.withProxyServer(proxyServer)

    override def withBody[T: BodyWritable](body: T): MonitoredWSRequest.RequestExtended =
      request.withBody(body)

    override def withMethod(method: String): MonitoredWSRequest.RequestExtended =
      request.withMethod(method)

    override def execute(method: String): Future[WSResponse] =
      request.execute(method)

    override def execute(): Future[WSResponse] =
      request.execute()

    def observe[O](f: => Future[O]): Future[O] = {
      val stackWalker: StackFrame =
        StackWalker
          .getInstance()
          .walk { x =>
            x
              .skip(2)
              .filter(x =>
                !x.getClassName().contains(MonitoredWSRequest.getClass.getSimpleName())
              )
              .findFirst()
          }
          .get()

      val endPointName: String =
        stackWalker.toStackTraceElement.getMethodName()

      val serviceName: String =
        stackWalker.toStackTraceElement.getClassName().replace("services.", "").replace("Service", "")

      val startTime = System.nanoTime
      f
        .transformWith {
          case Failure(exception) =>
            externalRequestDuration.labels(
              serviceName,
              endPointName
            )
              .observe(Int.MaxValue)
            Future.failed(exception)
          case Success(output) =>
            val endTime     = System.nanoTime
            val requestTime = (endTime - startTime) / Collector.NANOSECONDS_PER_SECOND
            externalRequestDuration.labels(
              serviceName,
              endPointName
            )
              .observe(requestTime)
            Future.successful(output)
        }
    }

    override def get(): Future[WSResponse] =
      observe(request.get())

    override def post[T: BodyWritable](body: T): Future[WSResponse] =
      observe(request.post(body))

    override def post(body: File): Future[WSResponse] =
      observe(request.post(body))

    override def post(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
      observe(request.post(body))

    override def patch[T: BodyWritable](body: T): Future[WSResponse] =
      observe(request.patch(body))

    override def patch(body: File): Future[WSResponse] =
      observe(request.patch(body))

    override def patch(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
      observe(request.patch(body))

    override def put[T: BodyWritable](body: T): Future[WSResponse] =
      observe(request.put(body))

    override def put(body: File): Future[WSResponse] =
      observe(request.put(body))

    override def put(body: Source[MultipartFormData.Part[Source[ByteString, _]], _]): Future[WSResponse] =
      observe(request.put(body))

    override def delete(): Future[WSResponse] =
      observe(request.delete())

    override def head(): Future[WSResponse] =
      observe(request.head())

    override def options(): Future[WSResponse] =
      observe(request.options())

  }

  private val externalRequestDuration: Histogram = Histogram.build()
    .name("external_request_duration_seconds")
    .help("Duration of external requests in seconds")
    .labelNames("service_name", "endpoint_name")
    .buckets(
      0.01,
      0.025,
      0.075,
      0.1,
      0.25,
      0.5,
      0.75,
      1.0,
      2.5,
      5.0,
      7.5,
      10.0,
      15.0,
      30.0,
      45.0
    )
    .register()

}

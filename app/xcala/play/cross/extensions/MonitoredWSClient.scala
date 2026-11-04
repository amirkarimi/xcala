package xcala.play.cross.extensions

import play.api.libs.ws.WSClient

import javax.inject._
import scala.concurrent.ExecutionContext

class MonitoredWSClient @Inject() (
    wsClient: WSClient
)(implicit val ec: ExecutionContext) extends WSClient {

  override def underlying[T]: T =
    wsClient.underlying

  override def url(url: String): MonitoredWSRequest.RequestExtended =
    wsClient.url(url)

  override def close(): Unit =
    wsClient.close()

}

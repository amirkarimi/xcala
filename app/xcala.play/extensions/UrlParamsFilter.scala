package xcala.play.extensions

import akka.stream.Materializer
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result

import javax.inject.Inject
import scala.concurrent.Future

object UrlParamsFilter {
  val potentiallyHarmfulPattern: String = "[\\(\\)]+"
}

class UrlParamsFilter @Inject() (implicit val mat: Materializer) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader)
      : Future[Result] = {
    val filteredQueryMaps = requestHeader.target.queryMap.map { case (key, values) =>
      key ->
        values.map(_.replaceAll(UrlParamsFilter.potentiallyHarmfulPattern, " ").trim.replaceAll(" +", " "))
    }

    val filteredTarget = requestHeader.target.withQueryString(filteredQueryMaps)
    val filteredHeader = requestHeader.withTarget(filteredTarget)
    nextFilter(filteredHeader)
  }

}

package xcala.play.extensions

import play.api.mvc.Filter
import akka.stream.Materializer
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import javax.inject.Inject
import akka.http.scaladsl.model.HttpHeader

object UrlParamsFilter {
  val harmfulPattern                   = ".*[\\(\\)]+.*".r
  def isUrlParamSafe(urlParam: String) = harmfulPattern.findFirstMatchIn(urlParam).isEmpty
}

class UrlParamsFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val filteredQueryMaps = requestHeader.target.queryMap.filter { case (key, values) =>
      values.forall(UrlParamsFilter.isUrlParamSafe)
    }

    val filteredTarget = requestHeader.target.withQueryString(filteredQueryMaps)
    val filteredHeader = requestHeader.withTarget(filteredTarget)
    nextFilter(filteredHeader)
  }

}

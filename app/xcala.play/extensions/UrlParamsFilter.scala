package xcala.play.extensions

import play.api.mvc.Filter
import akka.stream.Materializer
import play.api.mvc.RequestHeader
import play.api.mvc.Result

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import javax.inject.Inject
import scala.util.matching.Regex

object UrlParamsFilter {
  val harmfulPattern: Regex                     = ".*[\\(\\)]+.*".r
  def isUrlParamSafe(urlParam: String): Boolean = harmfulPattern.findFirstMatchIn(urlParam).isEmpty
}

class UrlParamsFilter @Inject() (implicit val mat: Materializer, ec: ExecutionContext) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader): Future[Result] = {
    val filteredQueryMaps = requestHeader.target.queryMap.filter { case (_, values) =>
      values.forall(UrlParamsFilter.isUrlParamSafe)
    }

    val filteredTarget = requestHeader.target.withQueryString(filteredQueryMaps)
    val filteredHeader = requestHeader.withTarget(filteredTarget)
    nextFilter(filteredHeader)
  }

}

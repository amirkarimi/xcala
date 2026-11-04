package xcala.play.extensions

import akka.stream.Materializer
import play.api.mvc.Filter
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.mvc.Results

import javax.inject.Inject
import scala.concurrent.Future

object RequestSecurityFilter {
  val potentiallyHarmfulPattern: String = "[\\(\\)]+"
}

class RequestSecurityFilter @Inject() (implicit val mat: Materializer) extends Filter {

  override def apply(nextFilter: RequestHeader => Future[Result])(requestHeader: RequestHeader)
      : Future[Result] =
    if (
      requestHeader.headers.toSimpleMap.values.exists(_.contains('\u0000')) ||
      requestHeader.uri.contains("%00")
    ) {
      Future.successful(Results.BadRequest("Null characters are not allowed."))
    } else {
      val filteredQueryMaps = requestHeader.target.queryMap.map { case (key, values) =>
        key ->
          values
            .map(
              _.replaceAll(RequestSecurityFilter.potentiallyHarmfulPattern, " ")
                .trim
                .replaceAll(" +", " ")
            )
      }

      val filteredTarget = requestHeader.target.withQueryString(filteredQueryMaps)
      val filteredHeader = requestHeader.withTarget(filteredTarget)
      nextFilter(filteredHeader)
    }

}

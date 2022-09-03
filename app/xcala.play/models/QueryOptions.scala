package xcala.play.models

import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Request

case class QueryOptions(
    page: Int = 1,
    pageSizeOpt: Option[Int] = None,
    override val sortExpression: Option[String] = None
) extends SortOptionsBase[QueryOptions](sortExpression) {
  lazy val pageIndex: Int     = page - 1
  lazy val pageSize: Int      = pageSizeOpt.getOrElse(QueryOptions.defaultPageSize)
  lazy val startRowIndex: Int = pageIndex * pageSize

  lazy val nextPage: QueryOptions = copy(page = page + 1)
  lazy val prevPage: QueryOptions = copy(page = page - 1)

  def resetSort(sortExpression: Option[String]): QueryOptions = copy(sortExpression = sortExpression, page = 1)
}

object QueryOptions {
  val defaultPageSize = 10

  val form: Form[QueryOptions] = Form(
    mapping(
      "page" -> default(number, 1),
      "size" -> optional(number),
      "sort" -> optional(text)
    )(QueryOptions.apply)(QueryOptions.unapply)
  )

  def getFromRequest()(implicit request: Request[_], formBinding: FormBinding): QueryOptions = {
    form.bindFromRequest.value.getOrElse(QueryOptions())
  }

}

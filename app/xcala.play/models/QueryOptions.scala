package xcala.play.models

import xcala.play.utils.LanguageSafeFormBinding

import play.api.data._
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.Request

final case class QueryOptions(
    page                       : Int            = 1,
    pageSizeOpt                : Option[Int]    = None,
    override val sortExpression: Option[String] = None
) extends SortOptionsBase[QueryOptions](sortExpression) {
  lazy val pageIndex    : Int = page - 1
  lazy val pageSize     : Int = pageSizeOpt.getOrElse(QueryOptions.defaultPageSize)
  lazy val startRowIndex: Int = pageIndex * pageSize

  lazy val nextPage: QueryOptions = copy(page = page + 1)
  lazy val prevPage: QueryOptions = copy(page = page - 1)

  def resetSort(sortExpression: Option[String], resetPagination: Boolean = true): QueryOptions =
    copy(sortExpression = sortExpression, page = if (resetPagination) 1 else page)

}

object QueryOptions {
  val defaultPageSize: Int = 10

  val form: Form[QueryOptions] = Form(
    mapping(
      "page" -> default(number, 1),
      "size" -> optional(number),
      "sort" -> optional(text)
    )(QueryOptions.apply)(QueryOptions.unapply)
  )

  def getFromRequest()(implicit
      request    : Request[_],
      formBinding: FormBinding,
      messages   : Messages
  ): QueryOptions =
    LanguageSafeFormBinding.bindForm(form).value.getOrElse(QueryOptions())

}

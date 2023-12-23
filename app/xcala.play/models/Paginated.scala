package xcala.play.models

import play.api.data.Form

final case class Paginated[A](
    data                 : Seq[A],
    totalCount           : Long,
    queryOptions         : QueryOptions,
    args                 : Map[String, String]                = Map.empty,
    rowToAttributesMapper: Option[A => Seq[(String, String)]] = None
) {
  def pageCount  : Int     = math.ceil(totalCount.toDouble / queryOptions.pageSize.toDouble).toInt
  def hasNextPage: Boolean = queryOptions.page < pageCount
  def hasPrevPage: Boolean = queryOptions.page > 1

  def nextPage: Paginated[A] = if (hasNextPage) {
    copy(queryOptions = queryOptions.nextPage)
  } else {
    this
  }

  def prevPage: Paginated[A] = if (hasPrevPage) {
    copy(queryOptions = queryOptions.prevPage)
  } else {
    this
  }

  def gotoPage(page: Int): Paginated[A] = copy(queryOptions = queryOptions.copy(page = page))

  def sort(sortExpression: Option[String]): Paginated[A] =
    copy(queryOptions = queryOptions.sort(sortExpression))

  def toQueryString: String = {
    val encodedArgs = args.view.mapValues(java.net.URLEncoder.encode(_, "UTF-8"))

    val queryStringData = QueryOptions.form.fill(queryOptions).data ++ encodedArgs

    queryStringData
      .map { data =>
        s"${data._1}=${data._2}"
      }
      .mkString("&")
  }

  def withRowToAttributesMapper(mapper: A => Seq[(String, String)]): Paginated[A] =
    copy(rowToAttributesMapper = Some(mapper))

}

object Paginated {

  def apply[A, B](
      dataWithTotalCount   : DataWithTotalCount[A],
      queryOptions         : QueryOptions,
      criteria             : Option[B],
      criteriaForm         : Form[B],
      rowToAttributesMapper: Option[A => Seq[(String, String)]]
  ): Paginated[A] = {
    // Form data contains all request data but we just need criteria data
    val criteriaArgs = criteria.map(c => criteriaForm.fill(c).data).getOrElse(Map.empty)

    Paginated(
      data                  = dataWithTotalCount.data,
      totalCount            = dataWithTotalCount.totalCount,
      queryOptions          = queryOptions,
      args                  = criteriaArgs,
      rowToAttributesMapper = rowToAttributesMapper
    )
  }

  def apply[A, B](
      dataWithTotalCount: DataWithTotalCount[A],
      queryOptions      : QueryOptions,
      criteria          : Option[B],
      criteriaForm      : Form[B]
  ): Paginated[A] =
    Paginated(
      dataWithTotalCount    = dataWithTotalCount,
      queryOptions          = queryOptions,
      criteria              = criteria,
      criteriaForm          = criteriaForm,
      rowToAttributesMapper = None
    )

  def apply[A, B](
      dataWithTotalCount: DataWithTotalCount[A],
      queryOptions      : QueryOptions
  ): Paginated[A] =
    Paginated(
      data                  = dataWithTotalCount.data,
      totalCount            = dataWithTotalCount.totalCount,
      queryOptions          = queryOptions,
      args                  = Map.empty,
      rowToAttributesMapper = None
    )

}

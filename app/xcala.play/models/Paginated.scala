package xcala.play.models

import play.api.data.Form

case class Paginated[A](data: Seq[A], totalCount: Long, queryOptions: QueryOptions, args: Map[String, String] = Map()) {
  def pageCount: Int = math.ceil(totalCount.toDouble / queryOptions.pageSize.toDouble).toInt
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

  def sort(sortExpression: Option[String]): Paginated[A] = copy(queryOptions = queryOptions.sort(sortExpression))

  def toQueryString: String = {
    val encodedArgs = args.mapValues(java.net.URLEncoder.encode(_, "UTF-8"))

    val queryStringData = QueryOptions.form.fill(queryOptions).data ++ encodedArgs

    queryStringData
      .map { data =>
        s"${data._1}=${data._2}"
      }
      .mkString("&")
  }

}

object Paginated {

  def apply[A](dataWithTotalCount: DataWithTotalCount[A], queryOptions: QueryOptions): Paginated[A] = {
    apply(dataWithTotalCount.data, dataWithTotalCount.totalCount, queryOptions)
  }

  def apply[A, B](
      data: Seq[A],
      totalCount: Long,
      queryOptions: QueryOptions,
      criteria: Option[B],
      criteriaForm: Form[B]
  ): Paginated[A] = {
    // Form data contains all request data but we just need criteria data
    val criteriaArgs = criteria.map(c => criteriaForm.fill(c).data).getOrElse(Map())

    apply(data, totalCount, queryOptions, criteriaArgs)
  }

  def apply[A, B](
      dataWithTotalCount: DataWithTotalCount[A],
      queryOptions: QueryOptions,
      criteria: Option[B],
      criteriaForm: Form[B]
  ): Paginated[A] = {
    apply(dataWithTotalCount.data, dataWithTotalCount.totalCount, queryOptions, criteria, criteriaForm)
  }

}

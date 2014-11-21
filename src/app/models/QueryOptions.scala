package xcala.play.models

import play.api.mvc.QueryStringBindable
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Request

case class QueryOptions(page: Int = 1, pageSizeOpt: Option[Int] = None, sortExpression: Option[String] = None) {
  lazy val pageIndex = page - 1
  lazy val pageSize = pageSizeOpt.getOrElse(QueryOptions.defaultPageSize)
  lazy val startRowIndex = pageIndex * pageSize
  lazy val sortInfos = sortExpression.toList.flatMap(expr => expr.split(",").map(s => SortInfo.fromExpression(s)))
  
  lazy val nextPage = copy(page = page + 1)
  lazy val prevPage = copy(page = page - 1)
  
  def sort(sortExpression: Option[String]) = {
    sortExpression match {
      case None => copy(sortExpression = None, page = 1)
      case Some(sort) => {
        
        val existingSortInfo = sortInfos.find(s => s.field == sort)
        
        val newSortInfos = existingSortInfo match {
          case Some(sortInfo) if sortInfo.direction == 1 =>
            // Toggle direction if exists and not descending
            sortInfos map { sortInfo =>
              if (sortInfo.field == sort) {
                sortInfo.toggleDirection
              } else {
                sortInfo
              }
            }
          case Some(sortInfo) if sortInfo.direction == -1 =>
            // Remove sort info if it was descending
            sortInfos.filter(_ != sortInfo)
          case _ =>
            // Add new one if not exists
            sortInfos :+ SortInfo(sort)
        }
        
        copy(sortExpression = Some(newSortInfos.mkString(",")), page = 1)
      }
    }
  }
}

object QueryOptions {
  val defaultPageSize = 10
  
  val form = Form(
	  mapping(
	    "page" -> default(number, 1),
	    "size" -> optional(number),
	    "sort" -> optional(text)
	  )(QueryOptions.apply)(QueryOptions.unapply)
	)  
	
	def getFromRequest()(implicit request: Request[_]): QueryOptions = {
    form.bindFromRequest.value.getOrElse(QueryOptions())
  }
}
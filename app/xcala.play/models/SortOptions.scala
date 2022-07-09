package xcala.play.models

import play.api.mvc.QueryStringBindable
import play.api.data._
import play.api.data.Forms._
import play.api.mvc.Request

abstract class SortOptionsBase[A <: SortOptionsBase[_]](val sortExpression: Option[String] = None) {
  lazy val sortInfos = sortExpression.toList.flatMap(expr => expr.split(",").map(s => SortInfo.fromExpression(s)))

  def resetSort(sortExpression: Option[String]): A

  def sort(sortExpression: Option[String]) = {
    sortExpression match {
      case None => resetSort(sortExpression = None)
      case Some(sort) => {

        val existingSortInfo = sortInfos.find(s => s.field == sort)

        val newSortInfos = existingSortInfo match {
          case Some(sortInfo) if sortInfo.direction == 1 =>
            // Toggle direction if exists and not descending
            sortInfos.map { sortInfo =>
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

        resetSort(sortExpression = Some(newSortInfos.mkString(",")))
      }
    }
  }

}

case class SortOptions(override val sortExpression: Option[String] = None)
    extends SortOptionsBase[SortOptions](sortExpression) {
  def resetSort(sortExpression: Option[String]) = copy(sortExpression = sortExpression)
}

object SortOptions {

  val form = Form(
    mapping(
      "sort" -> optional(text)
    )(SortOptions.apply)(SortOptions.unapply)
  )

  def getFromRequest()(implicit request: Request[_], formBinding: FormBinding): SortOptions = {
    form.bindFromRequest.value.getOrElse(SortOptions())
  }

}

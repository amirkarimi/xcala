package xcala.play.models

import xcala.play.utils.LanguageSafeFormBinding

import play.api.data._
import play.api.data.Forms._
import play.api.i18n.Messages
import play.api.mvc.Request

abstract class SortOptionsBase[A <: SortOptionsBase[_]](val sortExpression: Option[String] = None) {

  lazy val sortInfos: Seq[SortInfo] =
    sortExpression.toSeq.flatMap((expr: String) => expr.split(",").map(s => SortInfo.fromExpression(s)))

  def resetSort(sortExpression: Option[String], resetPagination: Boolean): A

  def sort(sortExpression: Option[String], addIdToSort: Boolean, resetPagination: Boolean = true): A = {
    sortExpression match {
      case None => resetSort(sortExpression = None, resetPagination)
      case Some(sort) =>
        val existingSortInfo = sortInfos.find(s => s.field == sort)

        val newSortInfos: Seq[SortInfo] = {
          existingSortInfo match {
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
              sortInfos.filterNot(si => (si == sortInfo) || (addIdToSort && si.field == "_id"))
            case _ =>
              // Add new one if not exists
              sortInfos :+ SortInfo(sort)
          }
        } ++ {
          if (addIdToSort && !sortInfos.exists(_.field == "_id")) Seq(SortInfo("_id")) else Nil
        }

        resetSort(sortExpression = Some(newSortInfos.mkString(",")), resetPagination)
    }
  }

  def withDefaultSort(sortExpression: String, addIdToSort: Boolean = false): A = {
    sortInfos match {
      case Nil =>
        sort(sortExpression = Some(sortExpression), addIdToSort = addIdToSort, resetPagination = false)
      case _ =>
        this.asInstanceOf[A]
    }
  }

}

final case class SortOptions(override val sortExpression: Option[String] = None)
    extends SortOptionsBase[SortOptions](sortExpression) {

  def resetSort(sortExpression: Option[String], resetPagination: Boolean = true): SortOptions =
    copy(sortExpression = sortExpression)

}

object SortOptions {

  val form: Form[SortOptions] = Form(
    mapping(
      "sort" -> optional(text)
    )(SortOptions.apply)(SortOptions.unapply)
  )

  def getFromRequest()(implicit
      request    : Request[_],
      formBinding: FormBinding,
      messages   : Messages
  ): SortOptions =
    LanguageSafeFormBinding.bindForm(form).value.getOrElse(SortOptions())

}

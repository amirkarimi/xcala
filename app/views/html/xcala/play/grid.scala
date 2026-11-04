package views.html.xcala.play

import xcala.play.models._

import play.api.i18n.Messages
import play.api.mvc.Call
import play.twirl.api.Html
import play.twirl.api.HtmlFormat

import reactivemongo.api.bson.BSONObjectID

object grid {

  def apply[A](paginated: Paginated[A], updateTarget: String = "")(
      columns: Col[A]*
  )(implicit messages: Messages): HtmlFormat.Appendable = {
    renderGrid(paginated = paginated, updateTarget = updateTarget, columns = columns, messages = messages)
  }

  def renderGrid[A](
      paginated      : Paginated[A],
      updateTarget   : String,
      columns        : Seq[Col[A]],
      messages       : Messages,
      isResponsive   : Boolean      = true,
      maybeCreateCall: Option[Call] = None
  ): HtmlFormat.Appendable = {
    val rows =
      paginated.data.map { row: A =>
        val dataAttributes = paginated.rowToAttributesMapper.map(_(row)).getOrElse(Nil)
        columns
          .map(c => (c, c.maybeFieldValueMapper(row), c.cssClass(row)))
          .collect {
            case (c, Some(value), cssClass) =>
              (c, value, cssClass)
          } -> dataAttributes
      }

    val columnNamesWithData: Seq[String] = rows.flatMap {
      case (columnsInfo, _) => columnsInfo.map {
          case (column, _, _) => column.name
        }
    }.distinct

    val filteredColumns: Seq[Col[A]] =
      columns.filter(c => columnNamesWithData.contains(c.name))

    views.html.xcala.play.gridView(
      filteredColumns,
      rows,
      paginated,
      updateTarget,
      maybeCreateCall,
      isResponsive
    )(messages)
  }

  def renderGridWithoutPagination[A](
      data           : Seq[A],
      columns        : Seq[Col[A]],
      messages       : Messages,
      isResponsive   : Boolean      = true,
      maybeCreateCall: Option[Call] = None
  ): HtmlFormat.Appendable = {
    val rows =
      data.map { row: A =>
        columns.map(c => (c, c.maybeFieldValueMapper(row), c.cssClass(row))).collect {
          case (c, Some(value), cssClass) => (c, value, cssClass)
        }
      }

    val columnNamesWithData: Seq[String] = rows.flatMap {
      columnsInfo =>
        columnsInfo.map {
          case (column, _, _) => column.name
        }
    }.distinct

    val filteredColumns: Seq[Col[A]] =
      columns.filter(c => columnNamesWithData.contains(c.name))

    views.html.xcala.play.gridViewWithoutPagination(filteredColumns, rows, maybeCreateCall, isResponsive)(
      messages
    )
  }

}

object gridWithPager {

  def apply[A](
      paginated      : Paginated[A],
      updateTarget   : String       = "",
      maybeCreateCall: Option[Call] = None
  )(
      columns: Col[A]*
  )(implicit messages: Messages): HtmlFormat.Appendable = {
    Html(
      grid
        .renderGrid(
          paginated       = paginated,
          updateTarget    = updateTarget,
          columns         = columns,
          messages        = messages,
          maybeCreateCall = maybeCreateCall
        )
        .body + pager(paginated).body
    )
  }

}

object gridWithoutPager {

  def apply[A](
      data           : Seq[A],
      maybeCreateCall: Option[Call] = None
  )(
      columns: Col[A]*
  )(implicit messages: Messages): HtmlFormat.Appendable = {
    Html(
      grid
        .renderGridWithoutPagination(
          data            = data,
          columns         = columns,
          messages        = messages,
          maybeCreateCall = maybeCreateCall
        )
        .body
    )
  }

}

object gridHeader {

  def apply(
      name          : String,
      sortExpression: String,
      addIdToSort   : Boolean,
      paginated     : Paginated[_],
      updateTarget  : String = ""
  )(implicit
      messages: Messages
  ): Html = {
    val colLabel = messages(name)

    sortExpression match {
      case "" => Html(s"$colLabel")
      case sort =>
        val url =
          paginated.sort(
            sortExpression = Some(sort),
            addIdToSort    = addIdToSort
          ).toQueryString

        val link = s"<a href='?$url' data-ajax='true' data-ajax-update-target='$updateTarget'>$colLabel</a>"

        val icon = paginated.queryOptions.sortInfos
          .find(_.field == sortExpression)
          .map { sortInfo =>
            val sortAlt = if (sortInfo.direction == -1) { "-alt" }
            else { "" }
            s"&nbsp;<span class='glyphicon glyphicon glyphicon-sort-by-attributes$sortAlt'></span>"
          }
          .getOrElse {
            ""
          }

        Html(link + icon)
    }
  }

  def apply(name: String)(implicit
      messages: Messages
  ): Html = {
    val colLabel = messages(name)
    Html(s"$colLabel")
  }

}

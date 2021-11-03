package views.html.xcala.play

import play.api.i18n.Messages
import xcala.play.models._
import play.twirl.api.HtmlFormat
import play.twirl.api.Html

object grid {
  def apply[A](paginated: Paginated[A], updateTarget: String = "")(columns: Col[A]*)(implicit messages: Messages): HtmlFormat.Appendable = {
    renderGrid(paginated, updateTarget, columns, messages)
  }

  def renderGrid[A](paginated: Paginated[A], updateTarget: String, columns: Seq[Col[A]], messages: Messages): HtmlFormat.Appendable = {
    val rows = paginated.data.map(row => columns.map(c => (c, c.fieldMapper(row), c.cssClass(row))))
    views.html.xcala.play.gridView(columns, rows, paginated, updateTarget)(messages)
  }
}

object gridWithPager {
  def apply[A](paginated: Paginated[A], renderSinglePagePager: Boolean = true, updateTarget: String = "")(columns: Col[A]*)(implicit messages: Messages): HtmlFormat.Appendable = {
    Html(
      grid.renderGrid(paginated, updateTarget, columns, messages) +
        (if (!renderSinglePagePager && paginated.pageCount <= 1) {
          ""
        } else {
          pager(paginated).body
        })
    )
  }
}

object gridHeader {
  def apply(name: String, sortExpression: String, paginated: Paginated[_], updateTarget: String = "")(implicit messages: Messages) = {
    val colLabel = messages(name)

    sortExpression match {
      case "" => Html(s"$colLabel")
      case sort => {
        val url = paginated.sort(Some(sort)).toQueryString

        val link = s"<a href='?$url' data-ajax='true' data-ajax-update-target='$updateTarget'>$colLabel</a>"

        val icon = paginated.queryOptions.sortInfos.find(_.field == sortExpression) map { sortInfo =>
          val sortAlt = if (sortInfo.direction == -1) { "-alt" } else { "" }
          s"&nbsp;<span class='glyphicon glyphicon glyphicon-sort-by-attributes$sortAlt'></span>"
        } getOrElse {
          ""
        }

        Html(link + icon)
      }
    }
  }
}

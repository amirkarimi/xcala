package views.html.xcala.play

import play.api.i18n.Messages
import xcala.play.models._
import play.twirl.api.HtmlFormat
import play.twirl.api.Html
import play.api.i18n.Lang

object grid {
  def apply[A](paginated: Paginated[A], updateTarget: String = "")(columns: Col[A]*)(implicit lang: Lang): HtmlFormat.Appendable = {
    renderGrid(paginated, updateTarget, columns, lang)
  }

  def renderGrid[A](paginated: Paginated[A], updateTarget: String, columns: Seq[Col[A]], lang: Lang): HtmlFormat.Appendable = {
    val rows = paginated.data.map(row => columns.map(c => (c, c.fieldMapper(row), c.cssClass(row))))
    views.html.xcala.play.gridView(columns, rows, paginated, updateTarget)(lang)
  }
}

object gridWithPager {
  def apply[A](paginated: Paginated[A], renderSinglePagePager: Boolean = true, updateTarget: String = "")(columns: Col[A]*)(implicit lang: Lang): HtmlFormat.Appendable = {
    Html(
      grid.renderGrid(paginated, updateTarget, columns, lang) +
      (if (!renderSinglePagePager && paginated.pageCount <= 1) {
        ""
      } else {
        pager(paginated).body
      })
    )
  }
}

object gridHeader {
  def apply(name: String, sortExpression: String, paginated: Paginated[_], updateTarget: String = "")(implicit lang: Lang) = {
    val colLabel = Messages(name)

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

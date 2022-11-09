package xcala.play.models

abstract class LinkBase(
    val title: String,
    val url: Option[String],
    val showInTitle: Boolean,
    val showInBreadcrumb: Boolean
)

final case class Link(override val title: String, override val url: Option[String] = None)
    extends LinkBase(title, url, true, true)

final case class BreadcrumbLink(override val title: String, override val url: Option[String] = None)
    extends LinkBase(title, url, false, true)

final case class TitleLink(override val title: String, override val url: Option[String] = None)
    extends LinkBase(title, url, true, false)

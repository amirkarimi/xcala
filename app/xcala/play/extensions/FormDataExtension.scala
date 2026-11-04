package xcala.play.extensions

import play.api.data.Form

object FormDataExtension {

  val queryOptionFormKeys: Seq[String] =
    Seq("page", "size", "sort", "paginatedParams")

  def isEmptyWithoutQueryOptions[A](form: Form[A]): Boolean =
    !form.data
      .exists {
        case (key, _) if queryOptionFormKeys.contains(key) => false
        case _                                             => true
      }

}

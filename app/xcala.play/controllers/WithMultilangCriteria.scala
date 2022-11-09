package xcala.play.controllers

import scala.concurrent.Future
import xcala.play.services._
import play.api.data.Form
import xcala.play.models._
import play.api.i18n.I18nSupport
import xcala.play.utils.WithExecutionContext

trait WithMultilangCriteria[A <: WithLang, B]
    extends MultilangDataReadController[A]
    with WithExecutionContext
    with I18nSupport {
  protected val readService: DataReadCriteriaService[A, B]

  def criteriaForm: Form[B]

  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    val requestCriteriaData = criteriaForm.bindFromRequest.data
    val modifiedData        = requestCriteriaData.filter(_._1 != "lang") + ("lang" -> request2Messages.lang.code)
    val criteriaOpt         = criteriaForm.bind(modifiedData.toMap).value

    readService.find(criteriaOpt, queryOptions).map { dataWithTotalCount =>
      Paginated(dataWithTotalCount, queryOptions, criteriaOpt, criteriaForm)
    }
  }

}

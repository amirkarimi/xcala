package xcala.play.controllers

import xcala.play.models._
import xcala.play.services._
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.I18nSupport

import scala.concurrent.Future

trait WithMultilangCriteria[A <: WithLang, B]
    extends MultilangDataReadController[A]
    with WithExecutionContext
    with I18nSupport {
  protected val readService: DataReadCriteriaService[A, B]

  def criteriaForm: Form[B]

  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    val requestCriteriaData = criteriaForm.bindFromRequest().data
    val modifiedData        = requestCriteriaData.filter(_._1 != "lang") + ("lang" -> request2Messages.lang.code)
    val criteriaOpt         = criteriaForm.bind(modifiedData.toMap).value

    readService.find(criteriaOpt, queryOptions).map { dataWithTotalCount =>
      Paginated(
        dataWithTotalCount = dataWithTotalCount,
        queryOptions = queryOptions,
        criteria = criteriaOpt,
        criteriaForm = criteriaForm
      )
    }
  }

}

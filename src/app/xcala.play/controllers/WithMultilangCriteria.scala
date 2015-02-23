package xcala.play.controllers

import scala.concurrent.Future
import play.api.mvc.Controller
import xcala.play.services._
import play.api.data.Form
import xcala.play.models._
import play.api.i18n.Lang
import xcala.play.utils.WithExecutionContext

trait WithMultilangCriteria[A <: WithLang, B] extends MultilangDataReadController[A] with WithExecutionContext {
  protected val readService: DataReadCriteriaService[A, B]

  def criteriaForm: Form[B]

  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_], lang: Lang): Future[Paginated[A]] = {
    val requestCriteriaData = criteriaForm.bindFromRequest.data
    val modifiedData = requestCriteriaData.toList.filter(_._1 != "lang") :+ ("lang" -> lang.code)
    val criteriaOpt = criteriaForm.bind(modifiedData.toMap).value

    readService.find(criteriaOpt, queryOptions).map { dataWithTotalCount =>
      Paginated(dataWithTotalCount, queryOptions, criteriaOpt, criteriaForm)
    }
  }
}

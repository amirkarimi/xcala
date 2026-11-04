package xcala.play.controllers

import xcala.play.models._
import xcala.play.services._
import xcala.play.utils.LanguageSafeFormBinding
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.I18nSupport

import scala.concurrent.Future

trait WithMultilangCriteria[Doc <: DocumentWithId with WithLang, Model, Criteria]
    extends MultilangDataReadController[Doc, Model]
    with WithExecutionContext
    with I18nSupport {

  protected val readService: DataReadWithCriteriaService[Doc, Model, Criteria]
    with DataReadSimpleService[Doc, Model]

  def criteriaForm: Form[Criteria]

  override def getPaginatedData(
      queryOptions: QueryOptions
  )(implicit request: RequestType[_]): Future[Paginated[Model]] = {
    val requestCriteriaData = LanguageSafeFormBinding.bindForm(criteriaForm).data
    val modifiedData        = requestCriteriaData.filter(_._1 != "lang") + ("lang" -> request2Messages.lang.code)
    val criteriaOpt         = criteriaForm.bind(modifiedData).value

    readService.find(criteriaOpt, queryOptions).map { dataWithTotalCount =>
      Paginated(
        dataWithTotalCount    = dataWithTotalCount,
        queryOptions          = queryOptions,
        criteria              = criteriaOpt,
        criteriaForm          = criteriaForm,
        rowToAttributesMapper = None
      )
    }
  }

}

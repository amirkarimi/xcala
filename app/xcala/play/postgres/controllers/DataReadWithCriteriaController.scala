package xcala.play.postgres.controllers

import xcala.play.controllers.WithComposableActions
import xcala.play.models._
import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.services._
import xcala.play.utils.LanguageSafeFormBinding
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.mvc._

import scala.concurrent.Future

trait DataReadWithCriteriaController[Id, Entity <: EntityWithId[Id], Model, Criteria]
    extends InjectedController
    with DataReadController[Id, Entity, Model]
    with WithComposableActions
    with WithExecutionContext {

  implicit val messagesProvider: MessagesProvider

  protected val readService: DataReadWithCriteriaService[Id, Entity, Model, Criteria]

  def criteriaForm: Form[Criteria]

  def indexView(paginated: Paginated[Model])(implicit request: RequestType[_]): Future[Result]

  def indexResultView(paginated: Paginated[Model])(implicit request: RequestType[_]): Future[Result]

  def transformCriteria(criteria: Criteria)(implicit @annotation.nowarn request: RequestType[_]): Criteria =
    criteria

  val rowToAttributesMapper: Option[Model => Seq[(String, String)]] = None

  def getPaginatedData(queryOptions: QueryOptions)(implicit
      request: RequestType[_]
  ): Future[Paginated[Model]] = {
    val filledCriteriaForm = LanguageSafeFormBinding.bindForm(criteriaForm)
    filledCriteriaForm.value match {
      case None =>
        Future.successful(
          Paginated(
            dataWithTotalCount    = DataWithTotalCount[Model](Nil, 0),
            queryOptions          = queryOptions,
            criteria              = None,
            criteriaForm          = criteriaForm,
            rowToAttributesMapper = rowToAttributesMapper
          )
        )
      case Some(criteria) =>
        val transformedCriteria = transformCriteria(criteria)
        readService.find(transformedCriteria, queryOptions).map { dataWithTotalCount =>
          Paginated(
            dataWithTotalCount    = dataWithTotalCount,
            queryOptions          = queryOptions,
            criteria              = Some(transformedCriteria),
            criteriaForm          = criteriaForm,
            rowToAttributesMapper = rowToAttributesMapper
          )
        }
    }

  }

}

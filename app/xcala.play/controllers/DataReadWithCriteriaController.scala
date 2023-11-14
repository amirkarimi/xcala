package xcala.play.controllers

import xcala.play.models._
import xcala.play.models.DocumentWithId
import xcala.play.services.DataReadWithCriteriaService
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.mvc._

import scala.concurrent.Future

trait DataReadWithCriteriaController[Doc <: DocumentWithId, Model, Criteria]
    extends InjectedController
    with DataReadController[Doc, Model]
    with WithFormBinding
    with WithExecutionContext {
  self: InjectedController =>
  implicit val messagesProvider: MessagesProvider

  protected val readService: DataReadWithCriteriaService[Doc, Model, Criteria]

  def criteriaForm: Form[Criteria]

  def indexView(paginated: Paginated[Model])(implicit request: RequestType[_]): Future[Result]

  def indexResultView(paginated: Paginated[Model])(implicit request: RequestType[_]): Future[Result]

  def transformCriteria(criteria: Criteria)(implicit @annotation.nowarn request: RequestType[_]): Criteria = criteria

  val rowToAttributesMapper: Option[Model => Seq[(String, String)]] = None

  override def getPaginatedData(
      queryOptions: QueryOptions
  )(implicit request: RequestType[_]): Future[Paginated[Model]] = {
    bindForm(criteriaForm).flatMap { filledCriteriaForm =>
      val criteriaOpt = filledCriteriaForm.value.map(transformCriteria)

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

}

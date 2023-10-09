package xcala.play.postgres.controllers

import xcala.play.postgres.models._
import xcala.play.postgres.services._
import xcala.play.postgres.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.MessagesProvider
import play.api.mvc._

import scala.concurrent.Future

trait DataReadCriteriaController[A, B]
    extends InjectedController
    with WithComposableActions
    with WithFormBinding
    with WithExecutionContext {

  implicit val messagesProvider: MessagesProvider

  protected def readCriteriaService: DataReadCriteriaService[A, B]

  def criteriaForm: Form[B]

  def indexView(paginated: Paginated[A])(implicit request: RequestType[_]): Future[Result]

  def indexResultView(paginated: Paginated[A])(implicit request: RequestType[_]): Future[Result]

  def transformCriteria(criteria: B)(implicit @annotation.nowarn request: RequestType[_]): B = criteria

  val rowToAttributesMapper: Option[A => Seq[(String, String)]] = None

  def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    bindForm(criteriaForm).flatMap { filledCriteriaForm =>
      filledCriteriaForm.value match {
        case None           =>
          Future.successful(
            Paginated(
              dataWithTotalCount = DataWithTotalCount[A](Nil, 0),
              queryOptions = queryOptions,
              criteria = None,
              criteriaForm = criteriaForm
            )
          )
        case Some(criteria) =>
          val transformedCriteria = transformCriteria(criteria)
          readCriteriaService.find(transformedCriteria, queryOptions).map { dataWithTotalCount =>
            Paginated(
              dataWithTotalCount = dataWithTotalCount,
              queryOptions = queryOptions,
              criteria = Some(transformedCriteria),
              criteriaForm = criteriaForm,
              rowToAttributesMapper = rowToAttributesMapper
            )
          }
      }
    }
  }

  def index: Action[AnyContent] = action.async { implicit request =>
    val queryOptions = QueryOptions.getFromRequest()

    getPaginatedData(queryOptions).flatMap { paginated =>
      request.headers.get("X-Requested-With") match {
        case Some(_) => indexResultView(paginated)
        case None    => indexView(paginated)
      }
    }
  }

}

package xcala.play.postgres.controllers

import xcala.play.controllers.WithComposableActions
import xcala.play.controllers.WithMainPageResults
import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.services._
import xcala.play.utils.LanguageSafeFormBinding
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.mvc._

import scala.concurrent.Future

import org.postgresql.util.PSQLException

trait DataCudController[Id, Entity <: EntityWithId[Id], Model]
    extends InjectedController
    with WithMainPageResults
    with WithComposableActions
    with WithExecutionContext
    with I18nSupport {

  protected def cudService: DataReadSimpleService[Id, Entity, Model]
    with DataRemoveService[Id, Entity]
    with DataSaveService[Id, Entity, Model]

  def defaultForm: Form[Model]

  def createView(form: Form[Model])(implicit request: RequestType[_]): Future[Result]

  def editView(form: Form[Model], model: Model)(implicit request: RequestType[_]): Future[Result]

  def create: Action[AnyContent] = action.async { implicit request =>
    createView(LanguageSafeFormBinding.bindForm(defaultForm).discardingErrors)
  }

  def createPost: Action[AnyContent] = action.async { implicit request =>
    val filledForm = LanguageSafeFormBinding.bindForm(defaultForm)

    filledForm.fold(
      formWithErrors => {
        createView(formWithErrors)
      },
      model => {
        cudService
          .insert(model)
          .map { _ =>
            successfulResult("message.successfulSave")
          }
          .recoverWith {
            case psqlException: PSQLException
                if psqlException.getMessage.contains("duplicate key value violates unique constraint") =>
              Future.successful(
                failedResult(Messages("error.thisItemAlreadyExists"))
              )

            case throwable: Throwable =>
              recoverSaveError(throwable, filledForm)
          }
      }
    )

  }

  def edit(id: Id): Action[AnyContent] = action.async { implicit request =>
    cudService.findById(id).flatMap {
      case Some(model) => editView(defaultForm.fill(model), model)
      case None        => Future.successful(defaultNotFound)
    }
  }

  def editPost(id: Id): Action[AnyContent] = action.async { implicit request =>
    cudService.findById(id).flatMap {
      case None => Future.successful(defaultNotFound)
      case Some(model) =>
        val boundForm  = defaultForm.fill(model)
        val filledForm = LanguageSafeFormBinding.bindForm(boundForm)

        filledForm.fold(
          formWithErrors => {
            editView(formWithErrors, model)
          },
          model =>
            cudService
              .update(model)
              .map { _ =>
                successfulResult("message.successfulSave")
              }
              .recoverWith { case throwable: Throwable =>
                recoverSaveError(throwable, filledForm)
              }
        )

    }
  }

  protected def recoverSaveError(throwable: Throwable, filledForm: Form[Model])(implicit
      request: RequestType[_]
  ): Future[Result] = {
    createView(filledForm.withGlobalError(throwable.getMessage))
  }

  def delete(id: Id): Action[AnyContent] = action.async { implicit request =>
    cudService.delete(id).map {
      case 1 => successfulResult("message.successfulDelete")
      case _ => defaultNotFound
    }
  }

  def defaultNotFound(implicit request           : RequestType[_]): Result
  def defaultInternalServerError(implicit request: RequestType[_]): Result

}

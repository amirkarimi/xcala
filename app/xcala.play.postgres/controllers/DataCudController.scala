package xcala.play.postgres.controllers

import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.services._
import xcala.play.postgres.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.Future

trait DataCudController[A <: EntityWithId]
    extends InjectedController
    with WithMainPageResults
    with WithFormBinding
    with WithComposableActions
    with WithExecutionContext
    with I18nSupport {

  protected def crudService: DataCrudService[A]

  def defaultForm: Form[A]

  def createView(form: Form[A])(implicit request: RequestType[_]): Future[Result]

  def editView(form: Form[A], model: A)(implicit request: RequestType[_]): Future[Result]

  def create: Action[AnyContent] = action.async { implicit request =>
    createView(defaultForm.bindFromRequest().discardingErrors)
  }

  def createPost: Action[AnyContent] = action.async { implicit request =>
    val filledFormFuture = bindForm(defaultForm)

    filledFormFuture.flatMap { filledForm =>
      filledForm.fold(
        formWithErrors => {
          createView(formWithErrors)
        },
        model => {
          crudService
            .insert(model)
            .map { _ =>
              successfulResult("message.successfulSave")
            }
            .recoverWith { case throwable: Throwable =>
              recoverSaveError(throwable, filledForm)
            }
        }
      )
    }
  }

  def edit(id: Long): Action[AnyContent] = action.async { implicit request =>
    crudService.findById(id).flatMap {
      case Some(model) => editView(defaultForm.fill(model), model)
      case None        => Future.successful(defaultNotFound)
    }
  }

  def editPost(id: Long): Action[AnyContent] = action.async { implicit request =>
    crudService.findById(id).flatMap {
      case None        => Future.successful(defaultNotFound)
      case Some(model) =>
        val boundForm        = defaultForm.fill(model)
        val filledFormFuture = bindForm(boundForm)

        filledFormFuture.flatMap { filledForm =>
          filledForm.fold(
            formWithErrors => {
              editView(formWithErrors, model)
            },
            model =>
              crudService
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
  }

  protected def recoverSaveError(throwable: Throwable, filledForm: Form[A])(implicit
      request: RequestType[_]
  ): Future[Result] = {
    createView(filledForm.withGlobalError(throwable.getMessage()))
  }

  def delete(id: Long): Action[AnyContent] = action.async { implicit request =>
    crudService.delete(id).map {
      case 1 => successfulResult("message.successfulDelete")
      case _ => defaultNotFound
    }
  }

  def defaultNotFound(implicit request: RequestType[_]): Result
  def defaultInternalServerError(implicit request: RequestType[_]): Result

}

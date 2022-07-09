package xcala.play.controllers

import play.api.Logger
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.mvc._
import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.commands.WriteResult
import xcala.play.services._

import scala.concurrent.Future
import xcala.play.utils.WithExecutionContext

trait DataCudController[A]
    extends InjectedController
    with WithMainPageResults
    with WithFormBinding
    with WithComposableActions
    with WithExecutionContext
    with I18nSupport {
  protected def cudService: DataReadService[A] with DataSaveService[A] with DataRemoveService

  def defaultForm: Form[A]

  def createView(form: Form[A])(implicit request: RequestType[_]): Future[Result]

  def editView(form: Form[A], model: A)(implicit request: RequestType[_]): Future[Result]

  def create = action.async { implicit request =>
    createView(defaultForm.bindFromRequest.discardingErrors)
  }

  def createPost = action.async { implicit request =>
    val filledFormFuture = bindForm(defaultForm)

    filledFormFuture.flatMap { filledForm =>
      filledForm.fold(
        formWithErrors => {
          DataCudController.logger.debug("Form Error on Create: " + formWithErrors.errors)
          createView(formWithErrors)
        },
        model => {
          cudService
            .insert(model)
            .map { objectId =>
              successfulResult(Messages("message.successfulSave"))
            }
            .recoverWith { case throwable: Throwable =>
              recoverSaveError(throwable, filledForm)
            }
        }
      )
    }
  }

  def edit(id: BSONObjectID) = action.async { implicit request =>
    cudService.findById(id).flatMap {
      case Some(model) => editView(defaultForm.fill(model), model)
      case None        => Future.successful(NotFound)
    }
  }

  def editPost(id: BSONObjectID) = action.async { implicit request =>
    cudService.findById(id).flatMap {
      case None => Future.successful(NotFound)
      case Some(model) =>
        val boundForm        = defaultForm.fill(model)
        val filledFormFuture = bindForm(boundForm)

        filledFormFuture.flatMap { filledForm =>
          filledForm.fold(
            formWithErrors => {
              DataCudController.logger.debug("Form Error on Edit: " + formWithErrors.errors)
              editView(formWithErrors, model)
            },
            model =>
              cudService
                .save(model)
                .map { objectId =>
                  successfulResult(Messages("message.successfulSave"))
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
    createView(filledForm.withGlobalError(throwable.getMessage))
  }

  def delete(id: BSONObjectID) = action.async { implicit request =>
    cudService
      .remove(id)
      .map { _ =>
        successfulResult(Messages("message.successfulDelete"))
      }
      .recover { case WriteResult.Exception(error) =>
        failedResult(error.message)
      }
  }

}

object DataCudController {
  private val logger = Logger("DataCudController")
}

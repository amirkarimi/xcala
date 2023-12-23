package xcala.play.controllers

import xcala.play.models.DocumentWithId
import xcala.play.services._
import xcala.play.utils.WithExecutionContext

import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.i18n.Messages
import play.api.mvc._

import scala.concurrent.Future

import reactivemongo.api.bson.BSONObjectID
import reactivemongo.api.commands.WriteResult

trait DataCudController[Doc <: DocumentWithId, Model, BodyType]
    extends InjectedController
    with WithMainPageResults
    with WithFormBinding
    with WithComposableActions
    with WithExecutionContext
    with I18nSupport {

  protected def cudService
      : DataReadSimpleService[Doc, Model] with DataRemoveService[Doc] with DataSaveService[Doc, Model]

  val bodyParser: BodyParser[BodyType]

  def defaultForm: Form[Model]

  @annotation.nowarn
  def modelValidation(model: Model): Future[Either[String, Unit]] = Future.successful(Right(model))

  def postBindFormValidation(
      f: Model => Future[Result]
  )(implicit requestHeader: RequestHeader): Model => Future[Result] = { model =>
    modelValidation(model).flatMap {
      case Right(_)           =>
        f(model)
      case Left(errorMessage) =>
        Future.successful(failedResult(errorMessage))
    }
  }

  def createView(form: Form[Model])(implicit request: RequestType[_]): Future[Result]

  def editView(form: Form[Model], model: Model)(implicit request: RequestType[_]): Future[Result]

  def create: Action[AnyContent] = action.async { implicit request =>
    createView(defaultForm.bindFromRequest().discardingErrors)
  }

  def createPost: Action[BodyType] = action.async(bodyParser) { implicit request: RequestType[_] =>
    val filledFormFuture = bindForm(defaultForm)

    filledFormFuture.flatMap { filledForm =>
      filledForm.fold(
        formWithErrors => {
          createView(formWithErrors)
        },
        postBindFormValidation { model =>
          cudService
            .insert(model)
            .map { _ =>
              successfulResult(Messages("message.successfulSave"))
            }
            .recoverWith { case throwable: Throwable =>
              recoverSaveError(throwable, filledForm)
            }
        }
      )
    }
  }

  def edit(id: BSONObjectID): Action[AnyContent] = action.async { implicit request =>
    cudService.findById(id).flatMap {
      case Some(model) => editView(defaultForm.fill(model), model)
      case None        => Future.successful(NotFound)
    }
  }

  def editPost(id: BSONObjectID): Action[BodyType] =
    action.async(bodyParser) { implicit request: RequestType[_] =>
      cudService.findById(id).flatMap {
        case None        => Future.successful(NotFound)
        case Some(model) =>
          val boundForm        = defaultForm.fill(model)
          val filledFormFuture = bindForm(boundForm)

          filledFormFuture.flatMap { filledForm =>
            filledForm.fold(
              formWithErrors => {
                editView(formWithErrors, model)
              },
              postBindFormValidation { model =>
                cudService
                  .save(model)
                  .map { _ =>
                    successfulResult(Messages("message.successfulSave"))
                  }
                  .recoverWith { case throwable: Throwable =>
                    recoverSaveError(throwable, filledForm)
                  }
              }
            )
          }
      }
    }

  protected def recoverSaveError(throwable: Throwable, filledForm: Form[Model])(implicit
      request: RequestType[_]
  ): Future[Result] = {
    createView(filledForm.withGlobalError(throwable.getMessage))
  }

  def delete(id: BSONObjectID): Action[AnyContent] = action.async { implicit request =>
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

package xcala.play.controllers

import play.api.Logger
import play.api.data.Form
import play.api.i18n.{Lang, Messages}
import play.api.mvc._
import reactivemongo.bson.BSONObjectID
import xcala.play.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait DataCudController[A] extends Controller with WithFormBinding with WithComposableActions with WithoutImplicitLang {
  protected def CudService: DataReadService[A] with DataSaveService[A] with DataRemoveService

  def mainPageRoute(implicit lang: Lang): Call

  def defaultForm: Form[A]

  def createView(form: Form[A])(implicit request: RequestType, lang: Lang): Future[Result]

  def editView(form: Form[A], model: A)(implicit request: RequestType, lang: Lang): Future[Result]

  def create(implicit lang: Lang) = Action { implicit request =>
    createView(defaultForm.bindFromRequest.discardingErrors)
  }

  def createPost(implicit lang: Lang) = Action { implicit request =>
    val filledFormFuture = bindForm(defaultForm)

    filledFormFuture flatMap { filledForm =>
      filledForm.fold(
        formWithErrors => {
          DataCudController.logger.debug("Form Error on Create: " + formWithErrors.errors)
          createView(formWithErrors)
        },
        model => {
          CudService.insert(model) map { objectId =>
            successfulResult(Messages("message.successfulSave"))
          } recoverWith {
            case throwable: Throwable => recoverSaveError(throwable, filledForm)
          }
        })
    }
  }

  def edit(lang: Lang, id: BSONObjectID) = Action(lang) { implicit request =>
    implicit val implicitLang = lang

    CudService.findById(id).flatMap { modelOption =>
      modelOption match {
        case Some(model) => editView(defaultForm.fill(model), model)
        case None => Future.successful(NotFound)
      }
    }
  }

  def editPost(lang: Lang, id: BSONObjectID) = Action(lang) { implicit request =>
    implicit val implicitLang = lang

    CudService.findById(id) flatMap {
      case None => Future.successful(NotFound)
      case Some(model) =>
        val boundForm = defaultForm.fill(model)
        val filledFormFuture = bindForm(boundForm)

        filledFormFuture flatMap { filledForm =>
          filledForm.fold(
            formWithErrors => {
              DataCudController.logger.debug("Form Error on Edit: " + formWithErrors.errors)
              editView(formWithErrors, model)
            },
            model =>
              CudService.save(model).map { objectId =>
                successfulResult(Messages("message.successfulSave"))
              } recoverWith {
                case throwable: Throwable => recoverSaveError(throwable, filledForm)
              }
          )
        }
    }
  }

  protected def recoverSaveError(throwable: Throwable, filledForm: Form[A])(implicit request: RequestType, lang: Lang): Future[Result] = {
    createView(filledForm.withGlobalError(throwable.getMessage()))
  }

  def delete(lang: Lang, id: BSONObjectID) = Action(lang) { implicit request =>
    implicit val implicitLang = lang

    CudService.remove(id).map {
      case error if error.ok => successfulResult(Messages("message.successfulDelete"))
      case _ => NotFound
    }
  }

  protected def successfulResult(message: String)(implicit lang: Lang): Result = {
    Redirect(mainPageRoute).flashing("success" -> message)
  }

  protected def failedResult(message: String)(implicit lang: Lang): Result = {
    Redirect(mainPageRoute).flashing("error" -> message)
  }
}

object DataCudController {
  private val logger = Logger("DataCudController")
}
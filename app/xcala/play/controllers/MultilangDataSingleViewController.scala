package xcala.play.controllers

import xcala.play.models._
import xcala.play.services.DataReadSimpleService
import xcala.play.utils.WithExecutionContext

import play.api.i18n.I18nSupport
import play.api.i18n.Lang
import play.api.i18n.MessagesApi
import play.api.mvc._

import scala.concurrent.Future

import reactivemongo.api.bson._

trait MultilangDataSingleViewController[Doc <: DocumentWithId with WithLang, Model <: WithLang]
    extends Results
    with WithComposableActions
    with WithExecutionContext
    with I18nSupport {
  implicit val messagesApi: MessagesApi

  protected val readService: DataReadSimpleService[Doc, Model]

  def singleView(model: Model)(implicit request: RequestType[_]): Future[Result]

  def defaultNotFound(implicit request: RequestType[_]): Result

  def view(
      id: BSONObjectID
  ): Action[AnyContent] = action.async { implicit request =>
    readService.findById(id).flatMap {
      case None => Future.successful(defaultNotFound)
      case Some(model) =>
        singleView(model).map(_.withLang(Lang(model.lang)))
    }
  }

}

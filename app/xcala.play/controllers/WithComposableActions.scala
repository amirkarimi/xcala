package xcala.play.controllers

import play.api.mvc._

trait WithComposableActions {
  type RequestType[A] <: Request[A]
  def action: ActionBuilder[RequestType, AnyContent]
}

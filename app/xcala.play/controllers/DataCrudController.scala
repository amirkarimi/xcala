package xcala.play.controllers

import xcala.play.models.DocumentWithId
import xcala.play.services._

import play.api.mvc.InjectedController

trait DataCrudController[Doc <: DocumentWithId, CUDModel, RModel, BodyType]
    extends DataReadController[Doc, RModel]
    with DataCudController[Doc, CUDModel, BodyType] {
  self: InjectedController =>

  protected def readService: DataReadService[Doc]

}

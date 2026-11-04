package xcala.play.controllers

import xcala.play.models.DocumentWithId

import play.api.mvc.InjectedController

trait DataCrudController[Doc <: DocumentWithId, CUDModel, RModel, BodyType]
    extends DataReadController[Doc, RModel]
    with DataCudController[Doc, CUDModel, BodyType] {
  self: InjectedController =>

}

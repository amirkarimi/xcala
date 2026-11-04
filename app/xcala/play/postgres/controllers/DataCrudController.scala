package xcala.play.postgres.controllers

import xcala.play.postgres.models.EntityWithId

import play.api.mvc.InjectedController

trait DataCrudController[Id, Entity <: EntityWithId[Id], CUDModel, RModel]
    extends DataReadController[Id, Entity, RModel]
    with DataCudController[Id, Entity, CUDModel] {
  self: InjectedController =>

}

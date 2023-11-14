package xcala.play.postgres.controllers

import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.services._

import play.api.mvc.InjectedController

trait DataCrudWithCriteriaController[Id, Entity <: EntityWithId[Id], CUDModel, RModel, Criteria]
    extends DataCrudController[Id, Entity, CUDModel, RModel]
    with DataReadWithCriteriaController[Id, Entity, RModel, Criteria] {
  self: InjectedController =>

  override val readService: DataReadWithCriteriaService[Id, Entity, RModel, Criteria]

}

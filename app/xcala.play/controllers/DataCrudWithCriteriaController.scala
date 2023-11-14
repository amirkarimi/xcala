package xcala.play.controllers

import xcala.play.models.DocumentWithId
import xcala.play.services._

import play.api.mvc.InjectedController

trait DataCrudWithCriteriaController[Doc <: DocumentWithId, CUDModel, RModel, Criteria, BodyType]
    extends DataCrudController[Doc, CUDModel, RModel, BodyType]
    with DataReadWithCriteriaController[Doc, RModel, Criteria] {
  self: InjectedController =>

  override val readService: DataReadWithCriteriaService[Doc, RModel, Criteria]

}

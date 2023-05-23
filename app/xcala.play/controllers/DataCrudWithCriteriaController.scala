package xcala.play.controllers

import xcala.play.services._

import play.api.mvc.InjectedController

trait DataCrudWithCriteriaController[A, B, BodyType] extends DataCrudController[A, BodyType] with WithCriteria[A, B] {
  self: InjectedController =>

  protected val defaultService: DataReadService[A]
    with DataRemoveService
    with DataSaveService[A]
    with DataReadCriteriaService[A, B]

  protected val readCriteriaService
      : DataReadService[A] with DataRemoveService with DataSaveService[A] with DataReadCriteriaService[A, B] =
    defaultService

}

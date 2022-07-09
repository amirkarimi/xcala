package xcala.play.controllers

import xcala.play.services._
import play.api.mvc.InjectedController

trait DataCrudWithCriteriaController[A, B] extends DataCrudController[A] with WithCriteria[A, B] {
  self: InjectedController =>

  protected val defaultService: DataReadService[A]
    with DataRemoveService
    with DataSaveService[A]
    with DataReadCriteriaService[A, B]

  protected val readCriteriaService = defaultService
}

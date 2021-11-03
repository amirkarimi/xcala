package xcala.play.controllers

import xcala.play.services._
import play.api.mvc.InjectedController
import xcala.play.models.Permission

trait DataCrudController[A] extends DataReadController[A] with DataCudController[A] {
  self: InjectedController =>

  protected def defaultService: DataReadService[A] with DataRemoveService with DataSaveService[A]
  protected def permissions: List[Permission] = List()

  protected def readService = defaultService
  protected def cudService = defaultService
}

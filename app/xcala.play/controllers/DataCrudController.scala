package xcala.play.controllers

import xcala.play.models.Permission
import xcala.play.services._

import play.api.mvc.InjectedController

trait DataCrudController[A] extends DataReadController[A] with DataCudController[A] {
  self: InjectedController =>

  protected def defaultService: DataReadService[A] with DataRemoveService with DataSaveService[A]
  protected def permissions: List[Permission] = List()

  protected def readService: DataReadService[A] with DataRemoveService with DataSaveService[A] = defaultService

  protected def cudService: DataReadService[A] with DataRemoveService with DataSaveService[A] = defaultService
}

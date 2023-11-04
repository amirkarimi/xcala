package xcala.play.postgres.controllers

import xcala.play.postgres.models.EntityWithId
import xcala.play.postgres.services._

trait DataCrudController[A <: EntityWithId, B, C] extends DataCudController[A] with DataReadCriteriaController[B, C] {
  protected def service  : DataCrudService[A] with DataReadCriteriaService[B, C]
  def crudService        : DataCrudService[A] with DataReadCriteriaService[B, C] = service
  def readCriteriaService: DataCrudService[A] with DataReadCriteriaService[B, C] = service
}

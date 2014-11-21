package xcala.play.controllers

import play.api.i18n.Lang

import scala.concurrent.ExecutionContext.Implicits.global
import xcala.play.services.DataReadCriteriaService
import reactivemongo.bson._
import play.api.mvc._
import play.api.data.Form
import scala.concurrent.Future
import xcala.play.models._

trait WithCriteria[A, B] extends DataReadController[A] with WithFormBinding {
  self: Controller =>
    
  protected val readCriteriaService: DataReadCriteriaService[A, B]
  
  def criteriaForm: Form[B]
  
  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType, lang: Lang): Future[Paginated[A]] = {
    bindForm(criteriaForm) flatMap { filledCriteriaForm =>
      val criteriaOpt = filledCriteriaForm.value
      
      readCriteriaService.find(criteriaOpt, queryOptions).map { dataWithTotalCount =>
        Paginated(dataWithTotalCount, queryOptions, criteriaOpt, criteriaForm)
      }
    }
  }
}
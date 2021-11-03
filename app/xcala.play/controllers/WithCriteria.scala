package xcala.play.controllers

import xcala.play.services.DataReadCriteriaService
import play.api.mvc._
import play.api.data.Form
import scala.concurrent.Future
import xcala.play.models._
import xcala.play.utils.WithExecutionContext

trait WithCriteria[A, B] extends DataReadController[A] with WithFormBinding with WithExecutionContext {
  self: InjectedController =>
    
  protected val readCriteriaService: DataReadCriteriaService[A, B]
  
  def criteriaForm: Form[B]
  
  override def getPaginatedData(queryOptions: QueryOptions)(implicit request: RequestType[_]): Future[Paginated[A]] = {
    bindForm(criteriaForm) flatMap { filledCriteriaForm =>
      val criteriaOpt = filledCriteriaForm.value
      
      readCriteriaService.find(criteriaOpt, queryOptions).map { dataWithTotalCount =>
        Paginated(dataWithTotalCount, queryOptions, criteriaOpt, criteriaForm)
      }
    }
  }
}
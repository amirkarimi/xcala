package xcala.play.services

import xcala.play.services._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import reactivemongo.bson._
import play.api.i18n.Lang
import xcala.play.models._
import xcala.play.utils.WithExecutionContext

trait TreeService[A, B <: TreeModelBase[B]] extends DataReadService[A] with DataDocumentHandler[A] with WithExecutionContext {
  def getModel(entity: A, children: List[B]): B
  
  def find(lang: Option[Lang]): Future[List[B]] = {
    findItemsUnder(None, lang)
  }
  
  private def findItemsUnder(parent: Option[BSONObjectID], lang: Option[Lang] = None): Future[List[B]] = {
    val query = (parent, lang) match {
      case (None, lang) => 
        BSONDocument(
          "lang" -> lang.map(_.code), 
          "parent" -> BSONDocument("$exists" -> false))
          
      case (Some(id), None) => BSONDocument("parent" -> id)
      case _ => throw new IllegalArgumentException
    }
    
    findItemsUnder(query)
  }
  
  protected def findItemsUnder(query: BSONDocument): Future[List[B]] = {
    val items = findQuery(query).sort(BSONDocument("order" -> 1)).cursor[BSONDocument].collect[List]()
    
    items flatMap { items =>
      val itemModels = items map { doc =>
        val id = doc.getAs[BSONObjectID]("_id") 
        findItemsUnder(id) map { childMenus =>
          getModel(documentHandler.read(doc), childMenus)
        }
      }
      
      Future.sequence(itemModels)
    }    
  }
  
  def getAllOptions(lang: Option[Lang], exclude: Option[BSONObjectID] = None): Future[List[(String, String)]] = {
    def getOptions(items: List[B], exclude: Option[BSONObjectID], parentTitle: Option[String] = None): List[(String, String)] = {
      val filteredMenus = exclude match {
        case None => items
        case Some(id) => items.filter(_.id != Some(id))
      }
      
      filteredMenus flatMap { item => 
        val itemTitle = parentTitle.map(_ + " » ").mkString + item.generalTitle
        (item.id.get.stringify, itemTitle) +: getOptions(item.children, exclude, Some(itemTitle))
      }
    }
    
    find(lang) map { items =>
      getOptions(items, exclude)
    }
  }
}
package xcala.play.services

import scala.concurrent.Future
import reactivemongo.api.bson._
import play.api.i18n.Lang
import xcala.play.models._
import xcala.play.utils.WithExecutionContext

trait TreeService[A, B <: TreeModelBase[B]] extends DataReadServiceImpl[A] with DataDocumentHandler[A] with WithExecutionContext {
  def getModel(entity: A, children: List[B]): B
  
  def find(lang: Option[Lang]): Future[List[B]] = {
    findItemsUnder(None, lang)
  }
  
  private def findItemsUnder(parentId: Option[BSONObjectID], lang: Option[Lang] = None): Future[List[B]] = {
    val query = (parentId, lang) match {
      case (None, lang) => 
        BSONDocument(
          "lang" -> lang.map(_.code), 
          "parentId" -> BSONDocument("$exists" -> false))
          
      case (Some(id), None) => BSONDocument("parentId" -> id)
      case _ => throw new IllegalArgumentException
    }
    
    findItemsUnder(query)
  }
  
  protected def findItemsUnder(query: BSONDocument): Future[List[B]] = {
    val itemsFuture = findQuery(query) flatMap { query =>
      query.sort(BSONDocument("order" -> 1)).cursor[BSONDocument]().collect[List]()
    }

    itemsFuture flatMap { items =>
      val itemModels = items map { doc =>
        val id = doc.getAsOpt[BSONObjectID]("_id")
        findItemsUnder(id) map { childMenus =>
          getModel(documentHandler.readOpt(doc).get, childMenus)
        }
      }
      
      Future.sequence(itemModels)
    }    
  }
  
  def getAllOptions(lang: Option[Lang], exclude: Option[BSONObjectID] = None): Future[List[(String, String)]] = {
    def getOptions(items: List[B], exclude: Option[BSONObjectID], parentTitle: Option[String] = None): List[(String, String)] = {
      val filteredMenus = exclude match {
        case None => items
        case Some(id) => items.filter(!_.id.contains(id))
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
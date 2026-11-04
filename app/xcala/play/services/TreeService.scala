package xcala.play.services

import xcala.play.models._
import xcala.play.utils.WithExecutionContext

import play.api.i18n.Lang

import scala.concurrent.Future

import reactivemongo.api.bson._

trait TreeService[Doc <: DocumentWithId, Model <: TreeModelBase[BSONObjectID, Model]]
    extends DataReadSimpleServiceImpl[Doc]
    with DataDocumentHandler[Doc]
    with WithExecutionContext {
  def getModel(entity: Doc, children: List[Model]): Model

  def find(lang: Option[Lang], initialDocument: BSONDocument = BSONDocument()): Future[List[Model]] = {
    findItemsUnder(initialDocument = initialDocument, parentId = None, lang = lang)
  }

  private def findItemsUnder(
      initialDocument: BSONDocument,
      parentId       : Option[BSONObjectID],
      lang           : Option[Lang] = None
  ): Future[List[Model]] = {
    val query = (parentId, lang) match {
      case (None, lang) =>
        initialDocument ++
          BSONDocument("lang" -> lang.map(_.code), "parentId" -> BSONDocument("$exists" -> false))

      case (Some(id), None) =>
        initialDocument ++ BSONDocument("parentId" -> id)
      case _ => throw new IllegalArgumentException
    }

    findItemsUnder(query, initialDocument)
  }

  protected def findItemsUnder(query: BSONDocument, initialDocument: BSONDocument): Future[List[Model]] = {
    val itemsFuture = findQuery(query).flatMap { query =>
      query.sort(BSONDocument("order" -> 1)).cursor[BSONDocument]().collect[List]()
    }

    itemsFuture.flatMap { items =>
      val itemModels = items.map { doc =>
        val id = doc.getAsOpt[BSONObjectID]("_id")
        findItemsUnder(initialDocument = initialDocument, parentId = id).map { childMenus =>
          getModel(documentHandler.readOpt(doc).get, childMenus)
        }
      }

      Future.sequence(itemModels)
    }
  }

  def getAllOptions(
      lang   : Option[Lang],
      exclude: Option[BSONObjectID] = None
  ): Future[List[(String, String)]] = {
    def getOptions(
        items      : List[Model],
        exclude    : Option[BSONObjectID],
        parentTitle: Option[String] = None
    ): List[(String, String)] = {
      val filteredMenus = exclude match {
        case None     => items
        case Some(id) => items.filter(!_.id.contains(id))
      }

      filteredMenus.flatMap { item =>
        val itemTitle = parentTitle.map(_ + " » ").mkString + item.generalTitle
        (item.id.get.stringify, itemTitle) +:
          getOptions(
            items       = item.children,
            exclude     = exclude,
            parentTitle = Some(itemTitle)
          )
      }
    }

    find(lang).map { items =>
      getOptions(items = items, exclude = exclude)
    }
  }

}

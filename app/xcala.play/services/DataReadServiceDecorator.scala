package xcala.play.services

import xcala.play.models.DataWithTotalCount
import xcala.play.models.DocumentWithId
import xcala.play.models.QueryOptions
import xcala.play.models.SortOptions
import xcala.play.services.DataReadSimpleService
import xcala.play.utils.WithExecutionContext

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import reactivemongo.api.bson._

trait DataReadServiceDecorator[Doc <: DocumentWithId, Model]
    extends DataReadSimpleService[Doc, Model]
    with WithExecutionContext {
  protected val service: DataReadSimpleService[Doc, Doc] with DataSaveService[Doc, Doc] with DataRemoveService[Doc]

  def mapModel(source: Doc): Future[Model]

  def getIdFromModel(model: Model): Option[BSONObjectID]

  private def mapOptional(maybeModel: Option[Doc]): Future[Option[Model]] = maybeModel match {
    case None        => Future.successful(None)
    case Some(model) => mapModel(model).map(Some(_))
  }

  protected def mapSeq(seq: Seq[Doc]): Future[List[Model]] = Future.sequence(seq.map(mapModel).toList)

  def findById(id: BSONObjectID): Future[Option[Model]] = service.findById(id).flatMap(mapOptional)

  def find(query: BSONDocument): Future[List[Model]] = service.find(query).flatMap(mapSeq)

  def find(query: BSONDocument, sort: BSONDocument): Future[List[Model]] = service.find(query, sort).flatMap(mapSeq)

  def findOne(query: BSONDocument): Future[Option[Model]] = service.findOne(query).flatMap(mapOptional)

  def count(query: BSONDocument): Future[Long] = service.count(query)

  def find(query: BSONDocument, queryOptions: QueryOptions): Future[DataWithTotalCount[Model]] = {
    service.find(query, queryOptions).flatMap { dataWithTotalCount =>
      mapSeq(dataWithTotalCount.data).map { convertedData =>
        dataWithTotalCount.copy(data = convertedData)
      }
    }
  }

  def find(query: BSONDocument, sortOptions: SortOptions): Future[Seq[Model]] =
    service.find(query, sortOptions).flatMap(mapSeq)

}

object DataReadServiceDecorator {

  final case class RelationMapper[Doc <: DocumentWithId, InnerModel <: DocumentWithId](
      val idLocator        : Doc => Option[BSONObjectID],
      val innerModelService: DataReadSimpleService[_, InnerModel]
  ) {

    def innerModelFinder(ids: Seq[BSONObjectID])(implicit ec: ExecutionContext): Future[Map[BSONObjectID, InnerModel]] =
      innerModelService.findInIds(ids).map { models =>
        models.groupBy(_.id.get).view.mapValues(_.head).toMap
      }

  }

  trait WithMapSeq[Doc <: DocumentWithId, Model] {

    def mapSeq(seq: Seq[Doc]): Future[List[Model]]

  }

  def mapSeqMaker[Doc <: DocumentWithId, Model, IM1 <: DocumentWithId](
      relationMapper1: RelationMapper[Doc, IM1]
  )(
      finalMapper    : Doc => Option[IM1] => Model
  )(implicit ec: ExecutionContext): WithMapSeq[Doc, Model] =
    new WithMapSeq[Doc, Model] {

      def mapSeq(docs: Seq[Doc]): Future[List[Model]] = {

        val relation1UsedIds: Seq[BSONObjectID] =
          docs.map(doc => relationMapper1.idLocator(doc)).flatten.distinct

        relationMapper1
          .innerModelFinder(relation1UsedIds)
          .map { innerModelHashMap1: Map[BSONObjectID, IM1] =>
            docs.map { doc: Doc =>
              val maybeId        : Option[BSONObjectID] = relationMapper1.idLocator(doc)
              val maybeInnerModel: Option[IM1]          = maybeId.map(x => innerModelHashMap1(x))

              finalMapper(doc)(maybeInnerModel)
            }.toList
          }
      }

    }

  def mapSeqMaker[Doc <: DocumentWithId, Model, IM1 <: DocumentWithId, IM2 <: DocumentWithId](
      relationMapper1: RelationMapper[Doc, IM1],
      relationMapper2: RelationMapper[Doc, IM2]
  )(
      finalMapper    : Doc => (Option[IM1], Option[IM2]) => Model
  )(implicit ec: ExecutionContext): WithMapSeq[Doc, Model] =
    new WithMapSeq[Doc, Model] {

      def mapSeq(docs: Seq[Doc]): Future[List[Model]] = {

        val relation1UsedIds: Seq[BSONObjectID] =
          docs.map(doc => relationMapper1.idLocator(doc)).flatten.distinct

        val relation2UsedIds: Seq[BSONObjectID] =
          docs.map(doc => relationMapper2.idLocator(doc)).flatten.distinct

        for {
          innerModelHashMap1 <- relationMapper1
            .innerModelFinder(relation1UsedIds)

          innerModelHashMap2 <- relationMapper2
            .innerModelFinder(relation2UsedIds)
        } yield {
          docs.map { doc: Doc =>
            val maybeId1        : Option[BSONObjectID] = relationMapper1.idLocator(doc)
            val maybeInnerModel1: Option[IM1]          = maybeId1.map(x => innerModelHashMap1(x))

            val maybeId2        : Option[BSONObjectID] = relationMapper2.idLocator(doc)
            val maybeInnerModel2: Option[IM2]          = maybeId2.map(x => innerModelHashMap2(x))

            finalMapper(doc)(maybeInnerModel1, maybeInnerModel2)
          }.toList
        }

      }

    }

}

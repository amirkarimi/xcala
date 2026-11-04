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

  protected val service: DataReadSimpleService[Doc, Doc] with DataSaveService[Doc, Doc]
    with DataRemoveService[Doc]

  def mapModel(source: Doc): Future[Model]

  def getIdFromModel(model: Model): Option[BSONObjectID]

  private def mapOptional(maybeModel: Option[Doc]): Future[Option[Model]] = maybeModel match {
    case None        => Future.successful(None)
    case Some(model) => mapModel(model).map(Some(_))
  }

  protected def mapSeq(seq: Seq[Doc]): Future[List[Model]] = Future.sequence(seq.map(mapModel).toList)

  def findById(id: BSONObjectID): Future[Option[Model]] = service.findById(id).flatMap(mapOptional)

  def find(query: BSONDocument): Future[List[Model]] = service.find(query).flatMap(mapSeq)

  def find(query: BSONDocument, sort: BSONDocument): Future[List[Model]] =
    service.find(query, sort).flatMap(mapSeq)

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

trait MapSeqBasedMapModel[Doc <: DocumentWithId, Model] {
  this: DataReadServiceDecorator[Doc, Model] =>

  override def mapModel(source: Doc): Future[Model] =
    mapSeq(Seq(source)).map(_.head)

}

object DataReadServiceDecorator {

  /*
    optional and sequence can be used as values for innerModelIterableMapper parameters
   */
  val optional: Option[_] => IterableOnce[_] =
    (x: Option[_]) =>
      x match {
        case None => None
        case Some(value) =>
          value match {
            case seq: Seq[_] => seq.headOption
            case _ => ???
          }
      }

  val sequence: Option[_] => IterableOnce[_] =
    (x: Option[_]) =>
      x match {
        case None => Seq.empty
        case Some(value) =>
          value match {
            case seq: Seq[_] => seq
            case _ => ???
          }
      }

  final case class RelationMapper[Doc, InnerModel, +K[_] <: IterableOnce[_]](
      docToKeySet                 : Doc => Set[BSONObjectID],
      allKeysToKeySetToInnerModels: Set[BSONObjectID] => Future[Map[Set[BSONObjectID], Seq[InnerModel]]]
  )(
      val innerModelIterableMapper: Option[_] => IterableOnce[_]
  ) {

    def docsToAllKeySetToInnerModelsMap(docs: Seq[Doc]): Future[Map[Set[BSONObjectID], Seq[InnerModel]]] = {
      val allKeys: Set[BSONObjectID] = docs.flatMap { doc =>
        docToKeySet(doc)
      }.toSet

      allKeysToKeySetToInnerModels(allKeys)
    }

  }

  object RelationMapper {

    def apply[Doc, InnerModel, K[_] <: IterableOnce[_]](
        docToKeySet             : Doc => Set[BSONObjectID],
        findAllInnerModelsInKeys: Set[BSONObjectID] => Future[Seq[InnerModel]],
        innerModelToKeySet      : InnerModel => Set[BSONObjectID]
    )(
        innerModelIterableMapper: Option[_] => IterableOnce[_]
    )(implicit ec: ExecutionContext): RelationMapper[Doc, InnerModel, K] =
      new RelationMapper(
        docToKeySet = docToKeySet,
        allKeysToKeySetToInnerModels = { ids: Set[BSONObjectID] =>
          findAllInnerModelsInKeys(ids).map { models: Seq[InnerModel] =>
            models.groupBy(innerModel => innerModelToKeySet(innerModel))
          }
        }
      )(innerModelIterableMapper)

    def apply[Doc, InnerModel, K[_] <: IterableOnce[_]](
        docToKeySet       : Doc => Set[BSONObjectID],
        innerModelService : DataReadSimpleService[_, InnerModel],
        innerModelToKeySet: InnerModel => Set[BSONObjectID]
    )(
        innerModelIterableMapper: Option[_] => IterableOnce[_]
    )(implicit ec: ExecutionContext): RelationMapper[Doc, InnerModel, K] =
      new RelationMapper(
        docToKeySet = docToKeySet,
        allKeysToKeySetToInnerModels = { ids: Set[BSONObjectID] =>
          innerModelService.findInIds(ids).map { models: Seq[InnerModel] =>
            models.groupBy(innerModel => innerModelToKeySet(innerModel))
          }
        }
      )(innerModelIterableMapper)

  }

  private def mapSeqMakerCore[
      Doc,
      Model,
      K[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMappers: List[RelationMapper[Doc, _, K]]
  )(
      finalMapper: Doc => List[IterableOnce[_]] => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] =
    Future
      .traverse(relationMappers) { relationMapper =>
        relationMapper.docsToAllKeySetToInnerModelsMap(docs)
      }
      .flatMap { listOfRelationMaps =>
        val arrayOfRelationMaps: Array[Map[Set[BSONObjectID], Seq[Any]]] = listOfRelationMaps.toArray

        Future.traverse(docs.toList) { doc =>
          finalMapper(doc)(
            relationMappers.zipWithIndex
              .map { case (relationMapper, index) =>
                val innerModelHashMap: Map[Set[BSONObjectID], Seq[Any]] = arrayOfRelationMaps(index)
                val docKeys          : Set[BSONObjectID]                = relationMapper.docToKeySet(doc)
                relationMapper.innerModelIterableMapper(innerModelHashMap.get(docKeys))
              }
          )
        }
      }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      K1[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1]
  )(
      finalMapper: Doc => K1[IM1] => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] =
    mapSeqMakerCore(docs)(
      List(relationMapper1)
    ) { doc =>
      {
        case innerModel1 :: Nil =>
          finalMapper(doc)(innerModel1.asInstanceOf[K1[IM1]])
        case _ =>
          ???
      }
    }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      IM2,
      K1[_] <: IterableOnce[_],
      K2[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1],
      relationMapper2: RelationMapper[Doc, IM2, K2]
  )(
      finalMapper: Doc => (K1[IM1], K2[IM2]) => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] =
    mapSeqMakerCore(docs)(
      List(relationMapper1, relationMapper2)
    ) { doc =>
      {
        case innerModel1 :: innerModel2 :: Nil =>
          finalMapper(doc)(
            innerModel1.asInstanceOf[K1[IM1]],
            innerModel2.asInstanceOf[K2[IM2]]
          )
        case _ =>
          ???
      }
    }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      IM2,
      IM3,
      K1[_] <: IterableOnce[_],
      K2[_] <: IterableOnce[_],
      K3[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1],
      relationMapper2: RelationMapper[Doc, IM2, K2],
      relationMapper3: RelationMapper[Doc, IM3, K3]
  )(
      finalMapper: Doc => (K1[IM1], K2[IM2], K3[IM3]) => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] =
    mapSeqMakerCore(docs)(
      List(relationMapper1, relationMapper2, relationMapper3)
    ) { doc =>
      {
        case innerModel1 :: innerModel2 :: innerModel3 :: Nil =>
          finalMapper(doc)(
            innerModel1.asInstanceOf[K1[IM1]],
            innerModel2.asInstanceOf[K2[IM2]],
            innerModel3.asInstanceOf[K3[IM3]]
          )
        case _ =>
          ???
      }
    }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      IM2,
      IM3,
      IM4,
      K1[_] <: IterableOnce[_],
      K2[_] <: IterableOnce[_],
      K3[_] <: IterableOnce[_],
      K4[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1],
      relationMapper2: RelationMapper[Doc, IM2, K2],
      relationMapper3: RelationMapper[Doc, IM3, K3],
      relationMapper4: RelationMapper[Doc, IM4, K4]
  )(
      finalMapper: Doc => (K1[IM1], K2[IM2], K3[IM3], K4[IM4]) => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] = {

    mapSeqMakerCore(docs)(
      List(relationMapper1, relationMapper2, relationMapper3, relationMapper4)
    ) { doc =>
      {
        case innerModel1 :: innerModel2 :: innerModel3 :: innerModel4 :: Nil =>
          finalMapper(doc)(
            innerModel1.asInstanceOf[K1[IM1]],
            innerModel2.asInstanceOf[K2[IM2]],
            innerModel3.asInstanceOf[K3[IM3]],
            innerModel4.asInstanceOf[K4[IM4]]
          )
        case _ =>
          ???
      }
    }

  }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      IM2,
      IM3,
      IM4,
      IM5,
      K1[_] <: IterableOnce[_],
      K2[_] <: IterableOnce[_],
      K3[_] <: IterableOnce[_],
      K4[_] <: IterableOnce[_],
      K5[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1],
      relationMapper2: RelationMapper[Doc, IM2, K2],
      relationMapper3: RelationMapper[Doc, IM3, K3],
      relationMapper4: RelationMapper[Doc, IM4, K4],
      relationMapper5: RelationMapper[Doc, IM5, K5]
  )(
      finalMapper: Doc => (K1[IM1], K2[IM2], K3[IM3], K4[IM4], K5[IM5]) => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] = {

    mapSeqMakerCore(docs)(
      List(relationMapper1, relationMapper2, relationMapper3, relationMapper4, relationMapper5)
    ) { doc =>
      {
        case innerModel1 :: innerModel2 :: innerModel3 :: innerModel4 :: innerModel5 :: Nil =>
          finalMapper(doc)(
            innerModel1.asInstanceOf[K1[IM1]],
            innerModel2.asInstanceOf[K2[IM2]],
            innerModel3.asInstanceOf[K3[IM3]],
            innerModel4.asInstanceOf[K4[IM4]],
            innerModel5.asInstanceOf[K5[IM5]]
          )
        case _ =>
          ???
      }
    }

  }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      IM2,
      IM3,
      IM4,
      IM5,
      IM6,
      K1[_] <: IterableOnce[_],
      K2[_] <: IterableOnce[_],
      K3[_] <: IterableOnce[_],
      K4[_] <: IterableOnce[_],
      K5[_] <: IterableOnce[_],
      K6[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1],
      relationMapper2: RelationMapper[Doc, IM2, K2],
      relationMapper3: RelationMapper[Doc, IM3, K3],
      relationMapper4: RelationMapper[Doc, IM4, K4],
      relationMapper5: RelationMapper[Doc, IM5, K5],
      relationMapper6: RelationMapper[Doc, IM6, K6]
  )(
      finalMapper: Doc => (K1[IM1], K2[IM2], K3[IM3], K4[IM4], K5[IM5], K6[IM6]) => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] = {

    mapSeqMakerCore(docs)(
      List(
        relationMapper1,
        relationMapper2,
        relationMapper3,
        relationMapper4,
        relationMapper5,
        relationMapper6
      )
    ) { doc =>
      {
        case innerModel1 :: innerModel2 :: innerModel3 :: innerModel4 :: innerModel5 :: innerModel6 :: Nil =>
          finalMapper(doc)(
            innerModel1.asInstanceOf[K1[IM1]],
            innerModel2.asInstanceOf[K2[IM2]],
            innerModel3.asInstanceOf[K3[IM3]],
            innerModel4.asInstanceOf[K4[IM4]],
            innerModel5.asInstanceOf[K5[IM5]],
            innerModel6.asInstanceOf[K6[IM6]]
          )
        case _ =>
          ???
      }
    }

  }

  def mapSeqMaker[
      Doc,
      Model,
      IM1,
      IM2,
      IM3,
      IM4,
      IM5,
      IM6,
      IM7,
      K1[_] <: IterableOnce[_],
      K2[_] <: IterableOnce[_],
      K3[_] <: IterableOnce[_],
      K4[_] <: IterableOnce[_],
      K5[_] <: IterableOnce[_],
      K6[_] <: IterableOnce[_],
      K7[_] <: IterableOnce[_]
  ](docs: Seq[Doc])(
      relationMapper1: RelationMapper[Doc, IM1, K1],
      relationMapper2: RelationMapper[Doc, IM2, K2],
      relationMapper3: RelationMapper[Doc, IM3, K3],
      relationMapper4: RelationMapper[Doc, IM4, K4],
      relationMapper5: RelationMapper[Doc, IM5, K5],
      relationMapper6: RelationMapper[Doc, IM6, K6],
      relationMapper7: RelationMapper[Doc, IM7, K7]
  )(
      finalMapper: Doc => (K1[IM1], K2[IM2], K3[IM3], K4[IM4], K5[IM5], K6[IM6], K7[IM7]) => Future[Model]
  )(implicit ec: ExecutionContext): Future[List[Model]] = {

    mapSeqMakerCore(docs)(
      List(
        relationMapper1,
        relationMapper2,
        relationMapper3,
        relationMapper4,
        relationMapper5,
        relationMapper6,
        relationMapper7
      )
    ) { doc =>
      {
        case innerModel1 :: innerModel2 :: innerModel3 :: innerModel4 :: innerModel5 :: innerModel6 ::
            innerModel7 :: Nil =>
          finalMapper(doc)(
            innerModel1.asInstanceOf[K1[IM1]],
            innerModel2.asInstanceOf[K2[IM2]],
            innerModel3.asInstanceOf[K3[IM3]],
            innerModel4.asInstanceOf[K4[IM4]],
            innerModel5.asInstanceOf[K5[IM5]],
            innerModel6.asInstanceOf[K6[IM6]],
            innerModel7.asInstanceOf[K7[IM7]]
          )
        case _ =>
          ???
      }
    }

  }

}

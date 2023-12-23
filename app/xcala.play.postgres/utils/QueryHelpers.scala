package xcala.play.postgres.utils

import xcala.play.models._
import xcala.play.models.SortOptionsBase

import slick.lifted._

object QueryHelpers {

  implicit class RichQuery[E, U, C[_]](val query: Query[E, U, C]) extends AnyVal {

    def optionalQuery[V](block: Query[E, U, C] => Option[Query[E, U, C]]): Query[E, U, C] = {
      block(query).getOrElse(query)
    }

    def multiSort[V <: Enumeration, A <: SortOptionsBase[A]](
        sortOptions    : SortOptionsBase[A],
        sortEnumeration: V
    )(
        f              : E => sortEnumeration.Value => Seq[ColumnOrdered[_]]
    ): Query[E, U, C] = {
      sortOptions.sortInfos.foldLeft(query) { (q, sortInfo) =>
        try {
          q.sortBy { e =>
            val sortFields = f(e)(sortEnumeration.withName(sortInfo.field))
            if (sortFields.size == 1) {
              sortInfo.direction match {
                case 1  => sortFields.head
                case -1 => sortFields.head.desc
              }
            } else {
              sortFields.reduceLeft[Ordered] {
                case (prev, next) if sortInfo.direction == 1 => (next, prev)
                case (prev, next)                            => (next.desc, prev)
              }
            }

          }
        } catch {
          case _: Throwable => q
        }
      }
    }

    def paginated(queryOptions: QueryOptions): Query[E, U, C] = {
      query.drop(queryOptions.startRowIndex).take(queryOptions.pageSize)
    }

    def multiSortPaginated[V <: Enumeration](
        queryOptions   : QueryOptions,
        sortEnumeration: V
    )(
        f              : E => sortEnumeration.Value => ColumnOrdered[_]
    ): Query[E, U, C] =
      multiSort(queryOptions, sortEnumeration)(f.andThen(_.andThen(Seq(_)))).paginated(queryOptions)

    def multiSortPaginated2[V <: Enumeration](
        queryOptions   : QueryOptions,
        sortEnumeration: V
    )(
        f              : E => sortEnumeration.Value => Seq[ColumnOrdered[_]]
    ): Query[E, U, C] =
      multiSort(queryOptions, sortEnumeration)(f).paginated(queryOptions)

  }

}

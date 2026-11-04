package xcala.play.postgres.utils

import org.joda.time.{DateTime, LocalDate}
import slick.ast.Node
import slick.jdbc.JdbcStatementBuilderComponent
import slick.util.SQLBuilder

trait JodaSlickExtras {
  import slick.lifted.Rep

  val toLocalDate: Rep[DateTime] => Rep[LocalDate]
  def dateDiff(unit    : String): (Rep[LocalDate], Rep[LocalDate]) => Rep[Int]
  def datetimeDiff(unit: String): (Rep[DateTime], Rep[DateTime]) => Rep[Int]
}

object PostgresJodaSlickExtras extends JodaSlickExtras {

  import com.github.tototoshi.slick.PostgresJodaSupport._
  import slick.jdbc.PostgresProfile.api._

  val toLocalDate: Rep[DateTime] => Rep[LocalDate] = {
    SimpleExpression.unary[DateTime, LocalDate] { (dateTime, queryBuilder) =>
      queryBuilder.sqlBuilder += "CAST("
      queryBuilder.expr(dateTime)
      queryBuilder.sqlBuilder += " AS DATE)"
    }
  }

  private def tDiff(
      unit        : String,
      node1       : Node,
      node2       : Node,
      queryBuilder: JdbcStatementBuilderComponent#QueryBuilder
  ): SQLBuilder = {
    queryBuilder.sqlBuilder += s"DATE_PART('$unit', "
    queryBuilder.expr(node2)
    queryBuilder.sqlBuilder += " - "
    queryBuilder.expr(node1)
    queryBuilder.sqlBuilder += ")"
  }

  def dateDiff(unit: String): (Rep[LocalDate], Rep[LocalDate]) => Rep[Int] = {
    SimpleExpression.binary[LocalDate, LocalDate, Int] { (fromDate, toDate, queryBuilder) =>
      tDiff(unit = unit, node1 = fromDate, node2 = toDate, queryBuilder = queryBuilder)
    }
  }

  def datetimeDiff(unit: String): (Rep[DateTime], Rep[DateTime]) => Rep[Int] = {
    SimpleExpression.binary[DateTime, DateTime, Int] { (fromDate, toDate, queryBuilder) =>
      tDiff(unit = unit, node1 = fromDate, node2 = toDate, queryBuilder = queryBuilder)
    }
  }

}

object H2JodaSlickExtras extends JodaSlickExtras {

  import com.github.tototoshi.slick.H2JodaSupport._
  import slick.jdbc.H2Profile.api._

  val toLocalDate: Rep[DateTime] => Rep[LocalDate] = {
    SimpleExpression.unary[DateTime, LocalDate] { (dateTime, queryBuilder) =>
      queryBuilder.sqlBuilder += "CAST("
      queryBuilder.expr(dateTime)
      queryBuilder.sqlBuilder += " AS DATE)"
    }
  }

  private def tDiff(
      unit        : String,
      node1       : Node,
      node2       : Node,
      queryBuilder: JdbcStatementBuilderComponent#QueryBuilder
  ): SQLBuilder = {
    queryBuilder.sqlBuilder += s"DATEDIFF('$unit', "
    queryBuilder.expr(node1)
    queryBuilder.sqlBuilder += ", "
    queryBuilder.expr(node2)
    queryBuilder.sqlBuilder += ")"
  }

  def dateDiff(unit: String): (Rep[LocalDate], Rep[LocalDate]) => Rep[Int] = {
    SimpleExpression.binary[LocalDate, LocalDate, Int] { (fromDate, toDate, queryBuilder) =>
      tDiff(unit = unit, node1 = fromDate, node2 = toDate, queryBuilder = queryBuilder)
    }
  }

  def datetimeDiff(unit: String): (Rep[DateTime], Rep[DateTime]) => Rep[Int] = {
    SimpleExpression.binary[DateTime, DateTime, Int] { (fromDate, toDate, queryBuilder) =>
      tDiff(unit = unit, node1 = fromDate, node2 = toDate, queryBuilder = queryBuilder)
    }
  }

}

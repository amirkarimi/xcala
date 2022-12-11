package xcala.play.utils

import scala.util.Failure
import scala.util.Success
import scala.util.Try

object SourceUtils {

  // TODO: to be replaced with Scala's scala.util.Using in Scala 2.13
  def using[T <: AutoCloseable, U](resource: T)(f: T => U): Try[U] =
    scala.util
      .Try(resource) match {

      case Failure(exception) =>
        Try(resource.close())
        Failure(exception)

      case Success(value) =>
        val fResult = Try(f(value))
        Try(resource.close())
        fResult

    }

}

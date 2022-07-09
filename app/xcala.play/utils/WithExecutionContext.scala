package xcala.play.utils

import scala.concurrent.ExecutionContext

trait WithExecutionContext {
  implicit def ec: ExecutionContext
}

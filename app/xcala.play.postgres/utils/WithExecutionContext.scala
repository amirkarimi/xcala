package xcala.play.postgres.utils

import scala.concurrent.ExecutionContext

trait WithExecutionContext {
  implicit def ec: ExecutionContext
}

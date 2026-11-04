package xcala.play.cross.utils

import play.api.inject.{BindingKey, Injector}

import javax.inject.{Inject, Singleton}
import scala.reflect.ClassTag

@Singleton
class LazyInjector @Inject() (injector: Injector) {
  LazyInjector.injector = injector
}

@SuppressWarnings(Array("NullAssignment"))
object LazyInjector extends Injector {
  var injector: Injector = null

  /** Get an instance of the given class from the injector.
    */
  def instanceOf[T: ClassTag]: T = injector.instanceOf[T]

  /** Get an instance of the given class from the injector.
    */
  def instanceOf[T](clazz: Class[T]): T = injector.instanceOf[T](clazz)

  /** Get an instance bound to the given binding key.
    */
  def instanceOf[T](key: BindingKey[T]): T = injector.instanceOf[T](key)
}

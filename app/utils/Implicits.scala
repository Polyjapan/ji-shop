package utils

import scala.concurrent.Future

/**
  * @author zyuiop
  */
object Implicits {

  implicit class PostFixAsFuture[T](value: T) {
    implicit def asFuture: Future[T] = Future.successful(value)
  }

}

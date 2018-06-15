package exceptions

/**
  * @author zyuiop
  */
case class OutOfStockException(item: Iterable[data.Product]) extends Exception(item.map(_.name).mkString(", ")) {

}

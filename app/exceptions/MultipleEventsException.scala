package exceptions

/**
  * @author zyuiop
  */
case class MultipleEventsException(eventIds: Set[Int]) extends Exception("multiple event ids: " + eventIds.mkString(", ")) {

}

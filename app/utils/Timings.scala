package utils

/**
  * @author zyuiop
  */
class Timings {
  var time = System.currentTimeMillis()

  def end(step: String) = {
    val old = time
    time = System.currentTimeMillis()

    println(s"$step took ${time - old} ms")
  }
}

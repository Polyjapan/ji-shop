package utils

import java.security.SecureRandom

import scala.util.Random

/**
  * @author zyuiop
  */
object RandomUtils {
  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val random = new Random(new SecureRandom())

  def randomString(length: Int): String = List.fill(30)(random.nextInt(chars.length)).map(chars).mkString
}

package utils

import org.specs2.mutable.Specification

/**
  * @author zyuiop
  */
class HashHelperTest extends Specification {

  "HashHelperTest" should {
    "check old passwords" in {
      val password = "abcd1234"
      val hash = "b60367d0f23f4cc5f29fbfcd97d136a2"

      new HashHelper().check("old", hash, password) shouldEqual true
      new HashHelper().check("old", "112233", password) shouldEqual false
      new HashHelper().check("old", hash, "1122333") shouldEqual false
    }

  }
}

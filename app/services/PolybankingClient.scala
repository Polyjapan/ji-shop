package services

import java.math.BigInteger
import java.security.MessageDigest

import data.CheckedOutItem
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.libs.ws.{DefaultWSCookie, WSClient}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class PolybankingClient @Inject()(config: Configuration, ws: WSClient)(implicit ec: ExecutionContext)  {
  lazy val configId: Int = config.get[Int]("polybanking.configId")
  lazy val requestKey: String = config.get[String]("polybanking.requestKey")
  lazy val ipnKey: String = config.get[String]("polybanking.ipnKey")
  lazy val url: String = config.get[String]("polybanking.baseUrl")
  lazy val postUrl: String = url + "/paiements/start/"

  private def computeSignature(data: Map[String, String], secret: String): String = {
    val digest = MessageDigest.getInstance("SHA-512")
    def escape(str: String) = str.replaceAll(";", "!!").replaceAll("=", "??")

    data.toList.sortBy(_._1).foreach(pair => {
      digest.update(escape(pair._1).getBytes)
      digest.update("=".getBytes)
      digest.update(escape(pair._2).getBytes)
      digest.update(";".getBytes)
      digest.update(secret.getBytes)
      digest.update(";".getBytes)
    })

    String.format("%032x", new BigInteger(1, digest.digest()))
  }

  def startPayment(amount: Double, orderId: Int, products: Iterable[CheckedOutItem]): Future[(Boolean, String)] = {
    val reference = Json.stringify(Json.toJson(products))

    val params = Map[String, String]("amount" -> (amount * 100).toInt.toString, "reference" -> ("trid#" + orderId.toString),
      "extra_data" -> reference,
      "config_id" -> configId.toString)

    val sig = prependWithZeroes(computeSignature(params, requestKey))

    println(sig)

    ws.url(postUrl).addCookies(DefaultWSCookie("gdpr", "accept")).post(params + ("sign" -> sig)).map(resp => {
      println(resp.body)
      val json = resp.json.as[JsObject]

      val status = json("status").as[String]
      if (status == "OK") {
        (true, json("url").as[String])
      } else {
        (false, status)
      }
    }).recover {
      case e: Throwable =>
        e.printStackTrace
        (false, "Exception")
    }
  }

  import PolybankingClient._

  private def removeLeadingZeroes(hex: String): String = {
    if (hex.head == '0') removeLeadingZeroes(hex.tail)
    else hex
  }

  private def prependWithZeroes(str: String): String = {
    if (str.length != 128) prependWithZeroes("0" + str)
    else str
  }

  def checkIpn(map: Map[String, Seq[String]]): IpnStatus = {
    val required = Seq("config", "reference", "postfinance_status", "postfinance_status_good", "last_update", "sign")
    val missing = required.filterNot(map.keySet)

    if (missing.nonEmpty) return MissingFields(missing)

    val single = map.view.mapValues(_.head).toMap
    val sig = removeLeadingZeroes(computeSignature(single - "sign", ipnKey))
    val packageSig = removeLeadingZeroes(single("sign"))

    if (sig != packageSig) return BadSignature(sig, packageSig)

    if (single("config") != configId.toString) return BadConfig

    val order = single("reference")
    if (!order.startsWith("trid#")) return MissingFields(Seq("reference"))

    try {
      val num = (order drop 5).toInt
      val isOk = single("postfinance_status_good").toBoolean

      CorrectIpn(isOk, num)
    } catch {
      case _: Throwable => MissingFields(Seq("reference", "postfinance_status_good"))
    }

  }
}

object PolybankingClient {
  sealed trait IpnStatus {
    def toError: String = this.toString
  }

  case class MissingFields(missingFields: Seq[String]) extends IpnStatus
  case class BadSignature(expected: String, got: String) extends IpnStatus {
    override def toError: String = "BadSignature"
  }
  case object BadConfig extends IpnStatus
  case class CorrectIpn(status: Boolean, orderId: Int) extends IpnStatus
}
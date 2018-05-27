package services

import java.math.BigInteger
import java.security.MessageDigest

import data.CheckedOutItem
import javax.inject.Inject
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.jcajce.provider.digest.SHA256
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
      println(pair)
    })

    String.format("%032x", new BigInteger(1, digest.digest()))
  }

  def startPayment(amount: Double, orderId: Int, products: Iterable[CheckedOutItem]): Future[(Boolean, String)] = {
    val reference = Json.stringify(Json.toJson(products))

    val params = Map[String, String]("amount" -> (0.01 * 100).toInt.toString, "reference" -> ("trid#" + orderId.toString),
      "extra_data" -> reference,
      "config_id" -> configId.toString)

    val sig = computeSignature(params, requestKey)

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
}

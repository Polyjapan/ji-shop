package services

import java.math.BigInteger
import java.security.MessageDigest
import java.sql.Timestamp
import java.util.Date

import data.{CheckedOutItem, Event}
import javax.inject.Inject
import org.joda.time.DateTime
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.{DefaultWSCookie, WSClient}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ReCaptchaClient @Inject()(config: Configuration, ws: WSClient)(implicit ec: ExecutionContext)  {
  lazy val secretKey: String = config.get[String]("recaptcha.secret")


  def checkCaptcha(response: String): Future[ReCaptchaResponse] = {
    val params = Map[String, String]("secret" -> secretKey, "response" -> response)
    implicit val tsreads: Reads[DateTime] = Reads.of[String] map (new DateTime(_))
    implicit val tswrites: Writes[DateTime] = Writes { dt: DateTime => JsString(dt.toString)}

    implicit val responseFormat: OFormat[ReCaptchaResponse] = Json.format[ReCaptchaResponse]

    ws.url("https://www.google.com/recaptcha/api/siteverify").post(params).map(resp => resp.json.as[ReCaptchaResponse])
  }

  case class ReCaptchaResponse(success: Boolean, challenge_ts: DateTime, hostname: String) // We can get the errors too
}


package controllers.users

import java.time.Clock

import ch.japanimpact.auth.api.cas.CASService
import constants.results.Errors._
import data.{Client, TemporaryToken}
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession
import play.api.Configuration
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
 * case None =>
 * val session = JwtSession(Seq[(String, JsValueWrapper)]("casId" -> userId, "casEmail" -> userEmail): _*)
 * Ok(Json.obj("success" -> true, "requireInfo" -> true, "errors" -> JsArray(), "token" -> session.serialize)).withJwtSession(session).asFuture
 * *
 * }
 *
 * @author zyuiop
 */
class LoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, cas: CASService)(implicit ec: ExecutionContext, config: Configuration, clock: Clock) extends AbstractController(cc) {

  def postLogin: Action[String] = Action.async(parse.text(1000)) { implicit request => {
    // Is it valid?
    val ticket = request.body

    cas.proxyValidate(ticket, None).flatMap {
      case Left(err) =>
        println("CAS Error: " + err + " (for ticket " + ticket + ")")
        notFound("ticket").asFuture
      case Right(user) if user.user.forall(_.isDigit)  =>
        val userId = user.user.toInt
        clients.findClientByCasId(userId).flatMap {
          case None =>
            val session = JwtSession() ++ ("casData" -> TemporaryToken(userId, user.lastname.get, user.firstname.get, user.email.get))

            Future.successful(Ok(Json.toJson(Json.obj("success" -> false, "requireInfo" -> true, "errors" -> JsArray(), "idToken" -> session.serialize))))
          case Some(client) =>
            clients.generateLoginResponse(client.id.get).map(e => Ok(Json.toJson(e)))
        }
      case r =>
        println("Invalid cas result " + r)
        notFound("ticket").asFuture
    }

  }
  }
}

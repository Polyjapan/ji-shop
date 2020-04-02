package controllers.users

import ch.japanimpact.auth.api.{AuthApi, TokenValidationService}
import constants.results.Errors._
import javax.inject.Inject
import models.ClientsModel
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
class LoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, api: AuthApi, tvs: TokenValidationService)(implicit ec: ExecutionContext, config: Configuration) extends AbstractController(cc) {

  def postLogin: Action[String] = Action.async(parse.text(1000)) { implicit request => {
    // Is it valid?
    val ticket = request.body

    tvs.validateToken(ticket) match {
      case Some(user) =>
        clients.findClientByCasId(user.userId).flatMap {
          case None =>
            Future.successful(Ok(Json.toJson(Json.obj("success" -> false, "requireInfo" -> true, "errors" -> JsArray()))))
          case Some(client) =>
            clients.generateLoginResponse(user.userId).map(e => Ok(Json.toJson(e)))
        }
      case None => notFound().asFuture
    }
  }
  }
}

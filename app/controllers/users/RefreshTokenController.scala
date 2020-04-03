package controllers.users

import java.time.Clock

import constants.ErrorCodes
import constants.results.Errors._
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt._
import play.api.Configuration
import play.api.libs.json._
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration

/**
  * @author zyuiop
  */
class RefreshTokenController @Inject()(cc: ControllerComponents, clients: ClientsModel)(implicit ec: ExecutionContext, config: Configuration, clock: Clock) extends AbstractController(cc) {
  def REFRESH_MAX_AGE(implicit conf:Configuration): Option[Long] = conf.getOptional[Duration]("play.http.refreshToken.maxAge").map(_.toSeconds)

  def getRefresh: Action[AnyContent] = Action.async { implicit request => {
    request.headers.get("Authorization")
      .filter(_.toLowerCase.startsWith("refresh"))
      .map(_.drop("refresh".length).trim)
      .map(JwtSession.deserialize)
      .filter(_.claim.isValid)
    match {
      case None =>
        Unauthorized.asError(ErrorCodes.AUTH_MISSING).asFuture
      case Some(token) =>
        val id = token.getAs[Int]("id")
        val clientId = token.getAs[Int]("clientId")

        if (id.isEmpty || clientId.isEmpty) {
          Unauthorized.asError(ErrorCodes.AUTH_MISSING).asFuture
        } else {
          clients.refreshIdToken(id.get, clientId.get).map {
            case Some((client, perms)) =>
              val idToken = JwtSession() + ("user", AuthenticatedUser(client, perms))
              Ok(Json.obj("success" -> true,
                "requireInfo" -> false, "errors" -> JsArray(),
                "idToken" -> idToken.serialize,
                "refreshToken" -> REFRESH_MAX_AGE.map(sec => token.withClaim(token.claim.expiresIn(sec))).getOrElse(token).serialize
              ))

            case None =>

              Forbidden.asError(ErrorCodes.NOT_FOUND)
          }
        }
    }

  }
  }
}

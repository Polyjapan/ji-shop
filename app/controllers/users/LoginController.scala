package controllers.users

import ch.japanimpact.auth.api.AuthApi.AppTicketResponse
import ch.japanimpact.auth.api.constants.GeneralErrorCodes
import ch.japanimpact.auth.api.{AuthApi, TicketType}
import constants.results.Errors._
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession._
import pdi.jwt._
import play.api.Configuration
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class LoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, api: AuthApi)(implicit ec: ExecutionContext, mailer: MailerClient, config: Configuration) extends AbstractController(cc) {

  def postLogin: Action[String] = Action.async(parse.text(250)) { implicit request => {
    // Is it valid?
    val ticket = request.body

    if (!api.isValidTicket(ticket)) {
      notFound().asFuture
    } else {
      api.getAppTicket(ticket).flatMap {
        case Left(AppTicketResponse(userId, userEmail, ticketType)) =>
          if (ticketType == TicketType.LoginTicket || ticketType == TicketType.PasswordResetTicket || ticketType == TicketType.EmailConfirmTicket) {
            // Get user

            clients.findClientByCasId(userId).map {
              case Some((client, perms)) =>
                val session = JwtSession() + ("user", AuthenticatedUser(client, perms))
                Ok(Json.obj("success" -> true, "errors" -> JsArray(), "token" -> session.serialize)).withJwtSession(session)

              case None =>
                val session = JwtSession(Seq[(String, JsValueWrapper)]("casId" -> userId, "casEmail" -> userEmail): _*)
                Ok(Json.obj("success" -> false, "errors" -> JsArray(), "token" -> session.serialize)).withJwtSession(session)

            }
          } else {
            // Invalid ticket type
            notFound().asFuture
          }


        case Right(error) if error == GeneralErrorCodes.InvalidAppSecret =>
          InternalServerError.asFuture
        case Right(error) if error == GeneralErrorCodes.MissingData =>
          notFound().asFuture
        case Right(error) if error == 201 =>
          notFound().asFuture
      }
    }
  }
  }

}

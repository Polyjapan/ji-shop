package controllers.users

import ch.japanimpact.auth.api.constants.GeneralErrorCodes
import ch.japanimpact.auth.api.{AppTicketResponse, AuthApi, TicketType}
import constants.results.Errors._
import data.Client
import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
              case None =>
                val session = JwtSession(Seq[(String, JsValueWrapper)]("casId" -> userId, "casEmail" -> userEmail): _*)
                Ok(Json.obj("success" -> true, "requireInfo" -> true, "errors" -> JsArray(), "token" -> session.serialize)).withJwtSession(session).asFuture

            }
 * @author zyuiop
 */
class LoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, api: AuthApi)(implicit ec: ExecutionContext, config: Configuration) extends AbstractController(cc) {

  def postLogin: Action[String] = Action.async(parse.text(250)) { implicit request => {
    // Is it valid?
    val ticket = request.body

    println("'" + ticket + "'")

    if (!api.isValidTicket(ticket)) {
      notFound().asFuture
    } else {
      api.getAppTicket(ticket).flatMap {
        case Left(AppTicketResponse(userId, userEmail, ticketType, _, user)) =>
          if (ticketType == TicketType.LoginTicket || ticketType == TicketType.PasswordResetTicket || ticketType == TicketType.EmailConfirmTicket) {
            // Get user

            clients.findClientByCasId(userId).flatMap {
              case None =>
                val client = Client(None, userId, user.details.lastName, user.details.firstName, userEmail, false)
                clients.createClient(client)
              case Some(client) =>
                Future.successful(client.id.get)
            }.flatMap(clientId => clients.generateLoginResponse(clientId).map(r => Ok(r)))

          } else {
            // Invalid ticket type
            notFound().asFuture
          }


        case Right(error) if error == GeneralErrorCodes.InvalidAppSecret =>
          InternalServerError.asFuture
        case Right(error) if error == GeneralErrorCodes.MissingData =>
          notFound().asFuture
        case Right(_) =>
          notFound().asFuture
      }
    }
  }
  }
}

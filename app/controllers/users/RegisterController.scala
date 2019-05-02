package controllers.users

import ch.japanimpact.auth.api.AuthApi.AppTicketResponse
import ch.japanimpact.auth.api.constants.GeneralErrorCodes
import ch.japanimpact.auth.api.{AuthApi, TicketType}
import constants.results.Errors._
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.{Jwt, JwtSession}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsArray, Json}
import play.api.libs.mailer._
import play.api.mvc._
import utils.HashHelper
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class RegisterController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, api: AuthApi)(implicit ec: ExecutionContext, mailerClient: MailerClient, config: Configuration) extends MessagesAbstractController(cc) with I18nSupport {
  private val registerForm = Form(mapping("ticket" -> nonEmptyText, "lastname" -> nonEmptyText, "firstname" -> nonEmptyText, "acceptNews" -> default(boolean, false))(RegisterForm.apply)(RegisterForm.unapply))

  case class RegisterForm(ticket: String, lastName: String, firstName: String, acceptNews: Boolean)

  def postSignup = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, formData => {
        if (!api.isValidTicket(formData.ticket)) {
          notFound("ticket").asFuture
        } else {
          api.getAppTicket(formData.ticket).flatMap {
            case Left(AppTicketResponse(userId, userEmail, TicketType.RegisterTicket, _)) =>
              clients
                .createClient(data.Client(Option.empty, userId, formData.lastName, formData.firstName, userEmail, acceptNewsletter = formData.acceptNews))
                .map(r => Ok(Json.obj("success" -> true, "errors" -> JsArray())))
            case Left(AppTicketResponse(_, _, TicketType.DoubleRegisterTicket, _)) =>
              Ok(Json.obj("success" -> true, "errors" -> JsArray())).asFuture
            case Left(AppTicketResponse(_, _, _, _)) =>
              // We might want to handle Login tickets
              notFound("ticket").asFuture
            case Right(error) if error == GeneralErrorCodes.InvalidAppSecret =>
              InternalServerError.asFuture
            case Right(_) =>
              notFound("ticket").asFuture
          }
        }
      }
    )
  }
  }

}

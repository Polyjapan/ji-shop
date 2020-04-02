package controllers.users

import ch.japanimpact.auth.api.{AuthApi, TokenValidationService, UserDetails, UserProfile}
import constants.results.Errors._
import data.Client
import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json.JsValue
import play.api.libs.mailer._
import play.api.mvc._
import utils.HashHelper
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
 * @author zyuiop
 */
class RegisterController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, api: AuthApi, tvs: TokenValidationService)(implicit ec: ExecutionContext, mailerClient: MailerClient, config: Configuration) extends MessagesAbstractController(cc) with I18nSupport {
  private val registerForm = Form(mapping("ticket" -> nonEmptyText, "acceptNews" -> default(boolean, false))(Tuple2.apply)(Tuple2.unapply))

  def postSignup: Action[JsValue] = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, {
        case (ticket, acceptNews) =>
          tvs.validateToken(ticket) match {
            case Some(_) =>
              api.getUserProfileByToken(ticket).flatMap {
                case Left(UserProfile(userId, userEmail, UserDetails(firstName, lastName, _), _)) =>
                  clients.findClientByCasId(userId).flatMap {
                    case None =>
                      val client = Client(None, userId, lastName, firstName, userEmail, acceptNews)
                      clients.createClient(client)
                    case Some(client) =>
                      clients.updateClient(client.copy(acceptNewsletter = acceptNews)).map(_ => client.casId)
                  }.flatMap(clientId => clients.generateLoginResponse(clientId).map(r => Ok(r)))
                case Right(_) =>
                  notFound().asFuture
              }
            case None => notFound("ticket").asFuture
          }
      }
    )
  }
  }

}

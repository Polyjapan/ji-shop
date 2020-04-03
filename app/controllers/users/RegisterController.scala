package controllers.users


import java.time.Clock

import ch.japanimpact.auth.api.cas.CASService
import constants.results.Errors._
import data.{Client, TemporaryToken}
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession._
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
class RegisterController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, cas: CASService)(implicit ec: ExecutionContext, mailerClient: MailerClient, config: Configuration, clock: Clock) extends MessagesAbstractController(cc) with I18nSupport {
  private val registerForm = Form(mapping("acceptNews" -> default(boolean, false))(t => t)(Some.apply))

  def postSignup: Action[JsValue] = Action.async(parse.json) { implicit request => {

    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, { acceptNews =>
        val ticket = Some(request.jwtSession)
          .filter(_.claim.isValid)
          .flatMap(_.getAs[TemporaryToken]("casData"))

        ticket match {
          case Some(user) =>
            val userId = user.casId
            clients.findClientByCasId(userId).flatMap {
              case None =>
                val client = Client(None, userId, user.lastname, user.firstname, user.email, acceptNews)
                clients.createClient(client)
              case Some(client) =>
                clients.updateClient(client.copy(acceptNewsletter = acceptNews)).map(_ => client.id.get)
            }.flatMap(clientId => clients.generateLoginResponse(clientId).map(r => Ok(r)))
          case r =>
            notFound("ticket").asFuture
        }
      }
    )
  }
  }

}

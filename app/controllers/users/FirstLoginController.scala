package controllers.users

import ch.japanimpact.auth.api.AuthApi
import constants.results.Errors._
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession._
import pdi.jwt._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class FirstLoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, api: AuthApi)(implicit ec: ExecutionContext, config: Configuration) extends AbstractController(cc) {
  private val registerForm = Form(mapping("ticket" -> nonEmptyText, "lastname" -> nonEmptyText, "firstname" -> nonEmptyText, "acceptNews" -> default(boolean, false))(RegisterForm.apply)(RegisterForm.unapply))

  case class RegisterForm(ticket: String, lastName: String, firstName: String, acceptNews: Boolean)

  def postFirstLogin: Action[JsValue] = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, formData => {
        val decoded = JwtSession.deserialize(formData.ticket)

        (decoded.getAs[Int]("casId"), decoded.getAs[String]("casEmail")) match {
          case (Some(id), Some(email)) =>
            clients.findClientByCasId(id).flatMap {
              case None =>
                val client = data.Client(Option.empty, id, formData.lastName, formData.firstName, email, acceptNewsletter = formData.acceptNews)
                clients
                  .createClient(client)
                  .flatMap(r => clients.generateLoginResponse(r).map(obj => Ok(obj)))
              case Some(client) =>
                clients.generateLoginResponse(client.id.get).map(obj => Ok(obj))
            }

          case _ =>
            notFound("ticket").asFuture
        }
      }
    )
  }
  }
}

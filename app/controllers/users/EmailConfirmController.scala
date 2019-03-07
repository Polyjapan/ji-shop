package controllers.users

import constants.results.Errors._
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession
import pdi.jwt.JwtSession._
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
class EmailConfirmController @Inject()(cc: ControllerComponents, clients: ClientsModel)(implicit ec: ExecutionContext, config: Configuration) extends AbstractController(cc) {
  private val emailConfirm = Form(mapping("email" -> email, "code" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))

  def emailConfirmProcess = Action.async(parse.json) { implicit request => {
    val form = emailConfirm.bindFromRequest

    form.fold(
      withErrors => formError(withErrors).asFuture,
      { case (email, code) =>
        if (code.length < 30) notFound("code").asFuture
        else clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            notFound("email")
          else {
            val client = opt.get._1
            if (!client.emailConfirmKey.contains(code)) {
              notFound("code")
            } else {
              val updatedClient = client.copy(emailConfirmKey = None)
              clients.updateClient(updatedClient)

              // Create the JWT Session
              // When a user verifies its email we can be sure it is legit, so we return an authentication token
              val session = JwtSession() + ("user", AuthenticatedUser(updatedClient, opt.get._2))
              Ok(Json.obj("success" -> true, "errors" -> JsArray(), "token" -> session.serialize)).withJwtSession(session)
            }
          }
        }
      })
  }
  }
}

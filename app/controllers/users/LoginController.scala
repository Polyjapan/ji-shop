package controllers.users

import constants.emails.EmailVerifyEmail
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession._
import pdi.jwt._
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import utils.Formats._
import utils.{HashHelper, RandomUtils}

import scala.concurrent.{ExecutionContext, Future}
import constants.results.Errors._
import play.api.Configuration
import play.api.libs.mailer.MailerClient
import utils.Implicits._
/**
  * @author zyuiop
  */
class LoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, hash: HashHelper)(implicit ec: ExecutionContext, mailer: MailerClient, config: Configuration) extends AbstractController(cc) {
  private val loginForm = Form(mapping("email" -> email, "password" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))

  private val CONFIRM_KEY_NOT_SENT = "__IMPORTED_ACCOUNT"

  def postLogin: Action[JsValue] = Action.async(parse.json) { implicit request: Request[JsValue] => {
    loginForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => {

        // If we have errors, we show the form again with the errors
        formError(withErrors).asFuture
      }, userData => {
        // If we have no error in the form itself we try to find the user data
        clients.findClient(userData._1).map { opt =>
          if (opt.isDefined) {
            // We have found a client: check the password
            val (client, perms) = opt.get

            if (hash.check(client.passwordAlgo, client.password, userData._2)) {
              if (client.emailConfirmKey.nonEmpty) {
                if (client.emailConfirmKey.get == CONFIRM_KEY_NOT_SENT) {
                  // This is an account with a not verified email address, but for which no code was set up at signup
                  // It likely means the user created the account on the previous website
                  // We will return the "not confirmed" error and, at the same time, generate the code & send an email containing the code

                  val code = RandomUtils.randomString(30)


                  clients.updateClient(client.copy(emailConfirmKey = Some(code)))
                  EmailVerifyEmail.sendVerifyEmail(client.email, code)
                }

                BadRequest.asError("error.email_not_confirmed")
              } else {
                // We try to upgrade the password of the user if it's using an insecure algo
                val newPasword = hash.upgrade(client.passwordAlgo, userData._2)

                // If there is a new password, it means the algorithm was insecure, so we have to update the user
                if (newPasword.nonEmpty) {
                  newPasword.get match {
                    case (algo, newHash) =>
                      val nc = client.copy(passwordAlgo = algo, password = newHash)
                      clients.updateClient(nc)
                  }
                }

                // Create the JWT Session
                val session = JwtSession() + ("user", AuthenticatedUser(client, perms))
                Ok(Json.obj("success" -> true, "errors" -> JsArray(), "token" -> session.serialize)).withJwtSession(session)
              }
            } else notFound()
          } else notFound()

        }
      }
    )
  }
  }

}

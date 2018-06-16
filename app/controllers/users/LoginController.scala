package controllers.users

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
import utils.HashHelper

import scala.concurrent.{ExecutionContext, Future}
import constants.results.Errors._
import utils.Implicits._
/**
  * @author zyuiop
  */
class LoginController @Inject()(cc: ControllerComponents, clients: ClientsModel, hash: HashHelper)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  private val loginForm = Form(mapping("email" -> email, "password" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))

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
              if (client.emailConfirmKey.nonEmpty)
                BadRequest.asError("error.email_not_confirmed")
              else {
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
                Ok(Json.obj("success" -> true, "errors" -> JsArray())).withJwtSession(session)
              }
            } else notFound()
          } else notFound()

        }
      }
    )
  }
  }

}

package controllers.users

import javax.inject.Inject
import models.ClientsModel
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import utils.Formats._

import scala.concurrent.{ExecutionContext, Future}
import constants.results.Errors._
import utils.Implicits._
/**
  * @author zyuiop
  */
class EmailConfirmController @Inject()(cc: ControllerComponents, clients: ClientsModel)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  private val emailConfirm = Form(mapping("email" -> email, "code" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))

  def emailConfirmProcess = Action.async(parse.json) { implicit request => {
    val form = emailConfirm.bindFromRequest

    form.fold(
      withErrors => formError(withErrors).asFuture,
      { case (email, code) =>
        clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            notFound("email")
          else {
            val client = opt.get._1
            if (!client.emailConfirmKey.contains(code)) {
              notFound("code")
            } else {
              clients.updateClient(client.copy(emailConfirmKey = None))
              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
            }
          }
        }
      })
  }
  }
}

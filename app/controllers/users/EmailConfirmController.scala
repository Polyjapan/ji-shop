package controllers.users

import javax.inject.Inject
import models.ClientsModel
import play.api.data.Forms._
import play.api.data._
import play.api.libs.json._
import play.api.mvc._
import utils.Formats._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class EmailConfirmController @Inject()(cc: ControllerComponents, clients: ClientsModel)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  private val emailConfirm = Form(mapping("email" -> email, "code" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))

  def emailConfirmProcess = Action.async(parse.json) { implicit request => {
    val form = emailConfirm.bindFromRequest

    form.fold(
      withErrors => Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors))),
      { case (email, code) =>
        clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("email", "error.not_found"))))
          else {
            val client = opt.get._1
            if (!client.emailConfirmKey.contains(code)) {
              NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("code", "error.not_found"))))
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

package controllers.pos

import constants.Permissions
import constants.results.Errors
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.OrdersModel
import models.PosModel
import pdi.jwt.JwtSession._
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class PosController @Inject()(cc: ControllerComponents, orders: OrdersModel, model: PosModel)(implicit ec: ExecutionContext, mailerClient: MailerClient) extends AbstractController(cc) {
  private val configForm = Form(mapping("name" -> nonEmptyText)(e => e)(Some(_)))

  def getConfigs: Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SELL_ON_SITE)) noPermissions.asFuture
    else {
      model.getConfigs.map(result => Ok(Json.toJson(result)))
    }
  }
  }

  def getConfig(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SELL_ON_SITE)) noPermissions.asFuture
    else {
      model
        .getFullConfig(id)
        .map {
          case Some(result) => Ok(Json.toJson(result))
          case None => Errors.notFound("id")
        }
    }
  }
  }

  // TODO: create endpoints to add/remove/move items

  def createConfig: Action[JsValue] = Action.async(parse.json) { implicit request => {
    handleConfig(config => {
      model.createConfig(config)
        .map(inserted => if (inserted == 1) Ok(Json.obj("success" -> true)) else dbError)
        .recover { case _ => dbError }
    })
  }
  }

  def updateConfig(id: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    handleConfig(config => {
      model.updateConfig(id, config)
        .map(inserted => if (inserted == 1) Ok(Json.obj("success" -> true)) else notFound("id"))
        .recover { case _ => dbError }
    })
  }
  }

  private def handleConfig(saver: PosConfiguration => Future[Result])(implicit request: Request[JsValue]): Future[Result] = {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.CHANGE_POS_CONFIGURATIONS)) noPermissions.asFuture
    else {
      configForm.bindFromRequest().fold(withErrors => {
        formError(withErrors).asFuture // If the name is absent from the request
      }, form => {
        val config = PosConfiguration(None, form)

        saver(config)
      })
    }
  }
}

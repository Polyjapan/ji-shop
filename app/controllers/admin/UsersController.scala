package controllers.admin

import constants.Permissions
import constants.results.Errors._
import data.{AuthenticatedUser, Event}
import javax.inject.Inject
import data.Client
import models.{ClientsModel, EventsModel, JsonClient, ProductsModel}
import pdi.jwt.JwtSession._
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._
import views.html.helper.form

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class UsersController @Inject()(cc: ControllerComponents, users: ClientsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {

  def getUsers: Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      users.allClients.map(e => Ok(Json.toJson(e.map(pair => JsonClient(pair)))))
    }
  }
  }

  def getUsersWithPermission(perm: String): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      users.allClientsWithPermission(perm).map(e => Ok(Json.toJson(e.map(pair => JsonClient(pair)))))
    }
  }
  }

  def getUser(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      users.getClient(id).map {
        case Some((client, perms)) => Ok(Json.toJson((JsonClient(client), perms)))
        case None => notFound("user")
      }
    }
  }
  }

  def addPermission(id: Int): Action[String] = Action.async(parse.text) { implicit request => {

    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      users.addPermission(id, request.body).map(nb => if (nb > 0) Ok.asSuccess else dbError)
    }
  }
  }

  def acceptEmail(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      users.forceEmailConfirm(id).map(nb => if (nb > 0) Ok.asSuccess else dbError)
    }
  }
  }

  def removePermission(id: Int): Action[String] = Action.async(parse.text) { implicit request => {

    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      users.removePermission(id, request.body).map(nb => if (nb > 0) Ok.asSuccess else notFound())
    }
  }
  }
}

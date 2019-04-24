package controllers.admin

import constants.Permissions._
import constants.results.Errors._
import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.AuthenticationPostfix._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class UsersController @Inject()(cc: ControllerComponents, users: ClientsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {

  def getUsers: Action[AnyContent] = Action.async {
    users.allClients.map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

  def getUsersWithPermission(perm: String): Action[AnyContent] = Action.async {
    users.allClientsWithPermission(perm).map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

  def getUser(id: Int): Action[AnyContent] = Action.async {
    users.getClient(id).map {
      case Some((client, perms)) => Ok(Json.toJson((client, perms)))
      case None => notFound("user")
    }
  } requiresPermission ADMIN_ACCESS

  def addPermission(id: Int): Action[String] = Action.async(parse.text) { request =>
    users.addPermission(id, request.body).map(nb => if (nb > 0) Ok.asSuccess else dbError)
  } requiresPermission ADMIN_ACCESS

  def removePermission(id: Int): Action[String] = Action.async(parse.text) { request =>
    users.removePermission(id, request.body).map(nb => if (nb > 0) Ok.asSuccess else notFound())
  } requiresPermission ADMIN_ACCESS
}

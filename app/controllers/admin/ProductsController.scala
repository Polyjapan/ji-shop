package controllers.admin

import constants.Permissions
import constants.results.Errors._
import data.{AuthenticatedUser, Product}
import javax.inject.Inject
import models.{EventsModel, ProductsModel, ScanningModel}
import pdi.jwt.JwtSession._
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.data.format.Formats
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ProductsController @Inject()(cc: ControllerComponents, products: ProductsModel, configs: ScanningModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {

  def getProducts(event: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      products.getProducts(event).map(e => Ok(Json.toJson(e)))
    }
  }
  }

  def getProduct(event: Int, id: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      products.getProduct(event, id).map(e => Ok(Json.toJson(e)))
    }
  }
  }

  def getAcceptingConfigs(event: Int, id: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      configs.getConfigsAcceptingProduct(event, id).map(e => Ok(Json.toJson(e)))
    }
  }
  }

  val form = Form(mapping(
    "id" -> optional(number),
    "name" -> nonEmptyText,
    "price" -> of(Formats.doubleFormat),
    "description" -> nonEmptyText,
    "longDescription" -> nonEmptyText,
    "maxItems" -> number,
    "eventId" -> number,
    "isTicket" -> boolean,
    "freePrice" -> boolean,
    "isVisible" -> boolean)(Product.apply)(Product.unapply))

  private def createOrUpdateProduct(handler: Product => Future[Int]): Action[JsValue] = Action.async(parse.json) { implicit request => {

    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      form.bindFromRequest.fold( // We bind the request to the form
        withErrors => {
          // If we have errors, we show the form again with the errors
          formError(withErrors).asFuture
        }, data => {
          handler(data).map(res =>
            if (res > 0) Ok.asSuccess else dbError
          )
        })
    }
  }
  }

  def createProduct(eventId: Int): Action[JsValue] = createOrUpdateProduct(
    p => products.createProduct(eventId, p.copy(Option.empty, eventId = eventId)))

  def updateProduct(eventId: Int, id: Int): Action[JsValue] = createOrUpdateProduct(
    p => products.updateProduct(eventId, id, p.copy(Some(id), eventId = eventId)))
}

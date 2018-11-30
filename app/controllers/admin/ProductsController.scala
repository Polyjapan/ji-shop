package controllers.admin

import constants.Permissions._
import constants.results.Errors._
import data.Product
import javax.inject.Inject
import models.{ProductsModel, ScanningModel}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.data.format.Formats
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ProductsController @Inject()(cc: ControllerComponents, products: ProductsModel, configs: ScanningModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {

  def getProducts(event: Int): Action[AnyContent] = Action.async {
    products.getProducts(event).map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

  def getProduct(event: Int, id: Int): Action[AnyContent] = Action.async {
    products.getProduct(event, id).map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

  def getAcceptingConfigs(event: Int, id: Int): Action[AnyContent] = Action.async {
    configs.getConfigsAcceptingProduct(event, id).map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

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
    "isVisible" -> boolean,
    "image" -> optional(text))(Product.apply)(Product.unapply))

  private def createOrUpdateProduct(handler: Product => Future[Int]): Action[JsValue] = Action.async(parse.json) { implicit request => {
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
  } requiresPermission ADMIN_PRODUCTS_MANAGE

  def createProduct(eventId: Int): Action[JsValue] = createOrUpdateProduct(
    p => products.createProduct(eventId, p.copy(Option.empty, eventId = eventId)))

  def updateProduct(eventId: Int, id: Int): Action[JsValue] = createOrUpdateProduct(
    p => products.updateProduct(eventId, id, p.copy(Some(id), eventId = eventId)))

  /**
    * Removes from the database all the products from an event that were not sold and that don't appear in a POS/Scan configuration
    * @param id the id of the event to purge
    */
  def purgeUnsoldProducts(id: Int): Action[AnyContent] = Action.async {
    products.purgeUnsoldProducts(id).map(r => Ok(Json.obj("purged" -> r, "success" -> true)))
  } requiresPermission ADMIN_PRODUCTS_MANAGE

}

package controllers.scanning

import constants.ErrorCodes
import constants.Permissions._
import constants.results.Errors
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.{AlreadyValidatedTicketException, OrdersModel, ProductsModel, ScanningModel, posConfigItems, posConfigurations}
import play.api.Configuration
import play.api.data.Forms.{mapping, _}
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ScanningController @Inject()(cc: ControllerComponents, orders: OrdersModel, scanModel: ScanningModel, products: ProductsModel)(implicit ec: ExecutionContext, mailerClient: MailerClient, conf: Configuration) extends AbstractController(cc) {
  private val scanForm = Form(mapping("barcode" -> nonEmptyText)(e => e)(Some(_)))
  private val configForm = Form(mapping("name" -> nonEmptyText, "acceptOrderTickets" -> boolean)(Tuple2.apply)(Tuple2.unapply))

  def scanCode(configId: Int): Action[AnyContent] = Action.async { implicit request => {
    scanForm.bindFromRequest().fold(
      withErrors => {
        formError(withErrors).asFuture // If the code is absent from the request
      }, barcode => {
        // We have the barcode
        // First, we query the scanning configuration
        // Then we query the ticket by its barcode [we could before check the ticket type as it is contained in the
        // barcode itself, but it would make the code larger and I don't think this would significantly impact
        // processing times]
        scanModel.getConfig(configId).flatMap(model => orders.findBarcode(barcode).map((model, _))).flatMap {
          case (None, _) =>
            Errors.notFound("config").asFuture
          case (_, None) =>
            Errors.notFound("barcode").asFuture
          case (Some((config, items)), Some((code, client, codeId))) =>
            // We've found both the config and the code
            code match {
              case OrdersModel.TicketBarCode(product, _, _) =>
                // We have a TicketBarCode <=> a barcode corresponding to a single product
                // We check if this barcode is accepted by this config
                if (items.map(_.acceptedItem).toSet.contains(product.id.get)) {
                  // Code accepted, we invalidate it and return the item scanned
                  invalidateCode(codeId, request.user, Json.obj("product" -> Json.toJsObject(product)))
                } else MethodNotAllowed.asFormError(FormError("", ErrorCodes.PRODUCT_NOT_ALLOWED, Seq(Json.toJson(product)))).asFuture
              case OrdersModel.OrderBarCode(_, products, _, _) =>
                // We have a OrderBarCode <=> a barcode corresponding to an order (a list of products)
                // We check if that config accepts order tickets
                if (config.acceptOrderTickets) {
                  // Code accepted, we invalidate it and return the list of items
                  invalidateCode(codeId, request.user, Json.obj("products" -> products.filterNot(pair => pair._1.isTicket), "user" -> (client.firstname + " " + client.lastname)))

                } else MethodNotAllowed.asError(ErrorCodes.PRODUCTS_ONLY).asFuture
            }
        }
      })
  }
  } requiresPermission SCAN_TICKET

  def getConfigs: Action[AnyContent] = Action.async {
    scanModel.getConfigs
      .map(result => Ok(Json.toJson(result.map(pair => Json.obj("event" -> pair._1, "configs" -> pair._2)))))
  } requiresPermission SCAN_TICKET

  def getConfigsForEvent(eventId: Int): Action[AnyContent] = Action.async {
    scanModel.getConfigsForEvent(eventId).map(result => Ok(Json.toJson(result)))
  } requiresPermission SCAN_TICKET

  def getConfig(eventId: Int, id: Int): Action[AnyContent] = Action.async {
    scanModel.getConfig(id).map(result => if (result.isDefined) Ok(Json.toJson(result.get._1)) else notFound("id"))
  } requiresPermission SCAN_TICKET

  def getFullConfig(eventId: Int, id: Int): Action[AnyContent] = Action.async {
    scanModel.getFullConfig(id).map(result => {
      if (result.isDefined) Ok(Json.toJson(result.map(pair => (pair._1, products.buildItemList(pair._2))).get))
      else notFound("id")
    })
  } requiresPermission SCAN_TICKET


  /**
    * Delete a scanning configuration
    * @param id the id of the scanning configuration to delete
    */
  def deleteConfig(eventId: Int, id: Int): Action[AnyContent] = Action.async {
    scanModel.deleteConfig(id).map(r => if (r >= 1) success else notFound("config"))
  } requiresPermission ADMIN_SCAN_MANAGE

  def createConfig(eventId: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    handleConfig(eventId, config => {
      scanModel.createConfig(config)
        .map(inserted => Ok(Json.toJson(inserted)))
        .recover { case _ => dbError }
    })
  } requiresPermission ADMIN_SCAN_MANAGE

  def updateConfig(eventId: Int, id: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    handleConfig(eventId, config => {
      scanModel.updateConfig(id, config.copy(Some(id)))
        .map(inserted => if (inserted == 1) Ok(Json.toJson(id)) else notFound("id"))
        .recover { case _ => dbError }
    })
  } requiresPermission ADMIN_SCAN_MANAGE

  def addProductToConfig(eventId: Int, id: Int): Action[String] = Action.async(parse.text) { implicit r =>
    addOrRemoveProduct(eventId, id, remove = false)
  } requiresPermission ADMIN_SCAN_MANAGE

  def removeProductFromConfig(eventId: Int, id: Int): Action[String] = Action.async(parse.text) { implicit r =>
    addOrRemoveProduct(eventId, id, remove = true)
  } requiresPermission ADMIN_SCAN_MANAGE

  private def addOrRemoveProduct(eventId: Int, id: Int, remove: Boolean)(implicit request: Request[String]): Future[Result] = {
    try {
      val productId = request.body.toInt

      scanModel.getConfig(id).flatMap(opt => {
        if (opt.isDefined) {
          if (remove) {
            if (opt.get._2.exists(e => e.acceptedItem == productId)) scanModel.removeProduct(id, productId).map(r => if (r == 1) success else dbError).recover { case _ => dbError }
            else success.asFuture // We don't have to remove, it's not there anymore
          } else {
            if (opt.get._2.exists(e => e.acceptedItem == productId)) success.asFuture // We don't have to insert, it's already there
            else {
              products.getOptionalProduct(eventId, productId).flatMap(opt => {
                if (opt.isDefined)
                  scanModel.addProduct(id, productId)
                    .map(r => if (r == 1) success else dbError)
                    .recover { case _ => dbError }
                else notFound("productId").asFuture
              })
            } // insert
          }
        } else notFound("config").asFuture
      })
    } catch {
      case _: NumberFormatException => BadRequest.asError("expected a number").asFuture
      case e: Throwable =>
        e.printStackTrace()
        unknownError.asFuture
    }
  }

  private def handleConfig(eventId: Int, saver: ScanningConfiguration => Future[Result])(implicit request: Request[JsValue]): Future[Result] = {
    configForm.bindFromRequest().fold(withErrors => {
      formError(withErrors).asFuture // If the code is absent from the request
    }, form => {
      val config = data.ScanningConfiguration(None, eventId, form._1, form._2)

      saver(config)
    })
  }


  /**
    * Check if a code is still valid, and invalidates it if so, returning some data
    *
    * @param codeId the id of the barcode to invalidate (the id in db, not the barcode itself)
    * @param user   the user who is performing the request and thus invalidating the code
    * @param data   the data to return if the barcode was still valid
    * @return
    */
  private def invalidateCode(codeId: Int, user: AuthenticatedUser, data: => JsObject) = {
    // Try to invalidate the barcode
    scanModel.invalidateBarcode(codeId, user.id).map(rep =>
      // The barcode was valid, check if it was correctly invalidated
      if (rep == 0) Errors.dbError // It wasn't.
      else Ok(data + ("success" -> Json.toJson(true)))
    ).recoverWith {
      // Something didn't work.
      case AlreadyValidatedTicketException(ticket, claimedBy) =>
        // The barcode was not valid anymore, we return an error with all the details
        MethodNotAllowed.asFormError(
          new FormError("", ErrorCodes.ALREADY_SCANNED,
            Seq(Json.obj(
              "scannedAt" -> ticket.claimedAt,
              "scannedBy" -> (claimedBy.firstname + " " + claimedBy.lastname),
                "ticketData" -> data
                )))
        ).asFuture
    }
  }
}

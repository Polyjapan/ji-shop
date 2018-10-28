package controllers.scanning

import constants.{ErrorCodes, Permissions}
import constants.results.Errors
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.{AlreadyValidatedTicketException, OrdersModel, ProductsModel, ScanningModel}
import pdi.jwt.JwtSession._
import play.api.data.Forms.{mapping, _}
import play.api.data.{Form, FormError}
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ScanningController @Inject()(cc: ControllerComponents, orders: OrdersModel, scanModel: ScanningModel, products: ProductsModel)(implicit ec: ExecutionContext, mailerClient: MailerClient) extends AbstractController(cc) {
  private val scanForm = Form(mapping("barcode" -> nonEmptyText)(e => e)(Some(_)))
  private val configForm = Form(mapping("name" -> nonEmptyText, "acceptOrderTickets" -> boolean)(Tuple2.apply)(Tuple2.unapply))

  def scanCode(configId: Int): Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SCAN_TICKET)) noPermissions.asFuture
    else {
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
                    invalidateCode(codeId, user, Json.obj("product" -> Json.toJsObject(product)))
                  } else MethodNotAllowed.asFormError(FormError("", ErrorCodes.PRODUCT_NOT_ALLOWED, Seq(Json.toJson(product)))).asFuture
                case OrdersModel.OrderBarCode(_, products, _, _) =>
                  // We have a OrderBarCode <=> a barcode corresponding to an order (a list of products)
                  // We check if that config accepts order tickets
                  if (config.acceptOrderTickets) {
                    // Code accepted, we invalidate it and return the list of items
                    invalidateCode(codeId, user, Json.obj("products" -> products.filterNot(pair => pair._1.isTicket), "user" -> (client.firstname + " " + client.lastname)))

                  } else MethodNotAllowed.asError(ErrorCodes.PRODUCTS_ONLY).asFuture
              }
          }
        })
    }
  }
  }

  def getConfigs: Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SCAN_TICKET)) noPermissions.asFuture
    else {
      scanModel.getConfigs.map(result => Ok(Json.toJson(result)))
    }
  }
  }

  def getConfig(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SCAN_TICKET)) noPermissions.asFuture
    else {
      scanModel.getConfig(id).map(result => if (result.isDefined) Ok(Json.toJson(result.get._1)) else notFound("id"))
    }
  }
  }

  def getFullConfig(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SCAN_TICKET)) noPermissions.asFuture
    else {
      scanModel.getFullConfig(id).map(result => {
        if (result.isDefined) Ok(Json.toJson(result.map(pair => (pair._1, products.buildItemList(pair._2))).get))
        else notFound("id")
      })
    }
  }
  }

  def createConfig: Action[JsValue] = Action.async(parse.json) { implicit request => {
    handleConfig(config => {
      scanModel.createConfig(config)
        .map(inserted => Ok(Json.toJson(inserted)))
        .recover { case _ => dbError }
    })
  }
  }

  def updateConfig(id: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    handleConfig(config => {
      scanModel.updateConfig(id, config.copy(Some(id)))
        .map(inserted => if (inserted == 1) Ok(Json.toJson(id)) else notFound("id"))
        .recover { case _ => dbError }
    })
  }
  }

  def addProductToConfig(id: Int): Action[String] = Action.async(parse.text) { implicit r =>
    addOrRemoveProduct(id, remove = false)
  }

  def removeProductFromConfig(id: Int): Action[String] = Action.async(parse.text) { implicit r =>
    addOrRemoveProduct(id, remove = true)
  }

  private def addOrRemoveProduct(id: Int, remove: Boolean)(implicit request: Request[String]): Future[Result] = {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.CHANGE_SCANNING_CONFIGURATIONS)) noPermissions.asFuture
    else {
      try {
        val productId = request.body.toInt

        scanModel.getConfig(id).flatMap(opt => {
          if (opt.isDefined) {
            if (remove) {
              if (opt.get._2.exists(e => e.acceptedItem == productId)) scanModel.removeProduct(id, productId).map(r => if (r == 1) success else dbError).recover { case _ => dbError }
              else success.asFuture // We don't have to remove, it's not there anymore
            } else {
              if (opt.get._2.exists(e => e.acceptedItem == productId)) success.asFuture // We don't have to insert, it's already there
              else scanModel.addProduct(id, productId).map(r => if (r == 1) success else dbError).recover { case _ => dbError } // insert
            }
          } else notFound("config").asFuture
        })
      } catch {
        case _: NumberFormatException => BadRequest.asError("expected a number").asFuture
        case _ => unknownError.asFuture
      }
    }
  }

  private def handleConfig(saver: ScanningConfiguration => Future[Result])(implicit request: Request[JsValue]): Future[Result] = {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.CHANGE_SCANNING_CONFIGURATIONS)) noPermissions.asFuture
    else {
      configForm.bindFromRequest().fold(withErrors => {
        formError(withErrors).asFuture // If the code is absent from the request
      }, form => {
        val config = data.ScanningConfiguration(None, form._1, form._2)

        saver(config)
      })
    }
  }



  /**
    * Check if a code is still valid, and invalidates it if so, returning some data
    *
    * @param codeId the id of the barcode to invalidate (the id in db, not the barcode itself)
    * @param user   the user who is performing the request and thus invalidating the code
    * @param data   the data to return if the barcode was still valid
    * @return
    */
  private def invalidateCode(codeId: Int, user: Option[AuthenticatedUser], data: => JsObject) = {
    // Try to invalidate the barcode
    scanModel.invalidateBarcode(codeId, user.get.id).map(rep =>
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
              "scannedBy" -> (claimedBy.firstname + " " + claimedBy.lastname
                ))))
        ).asFuture
    }
  }
}

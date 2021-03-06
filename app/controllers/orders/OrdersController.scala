package controllers.orders

import constants.Permissions
import javax.inject.Inject
import models.{JsonOrder, JsonOrderData, OrdersModel}
import play.api.Configuration
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import services.PdfGenerationService
import utils.AuthenticationPostfix._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class OrdersController @Inject()(cc: ControllerComponents, orders: OrdersModel, pdfGen: PdfGenerationService)(implicit ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {

  def getOrders = Action.async { implicit request =>
    orders.loadOrders(request.user.id, request.user.hasPerm(Permissions.SEE_ALL_ORDER_TYPES)).map(ords => Ok(
      JsArray(ords.map(ord => Json.toJson(JsonOrder(ord))))
    ))
  }.requiresAuthentication

  def getOrder(orderId: Int) = Action.async { implicit request =>
    orders.loadOrder(orderId, request.user.hasPerm(Permissions.VIEW_DELETED_STUFF))
      .map(opt =>
        opt.filter(data => data.order.clientId == request.user.id || request.user.hasPerm(Permissions.VIEW_OTHER_ORDER)))
      .map {
        case Some(data) => Ok(Json.toJson(JsonOrderData(data)))
        case None => NotFound
      }
  }.requiresAuthentication

  def getInvoice(orderId: Int) = Action.async { implicit request =>
    orders.getOrderDetails(orderId)
      .map(opt => opt.filter {
        case (order, client, _, _) =>
          order.paymentConfirmed.nonEmpty &&
            (client.id.get == request.user.id || request.user.hasPerm(Permissions.VIEW_OTHER_ORDER))
      })
      .map {
        case Some((order, client, event, products)) =>
          Ok(pdfGen.genInvoice(client, event, order, products)._2).as("application/pdf")
        case None => NotFound
      }
  }.requiresAuthentication
}

package controllers.orders

import constants.Permissions
import constants.results.Errors
import data._
import javax.inject.Inject
import models.{JsonOrder, JsonOrderData, OrdersModel}
import pdi.jwt.JwtSession._
import play.api.data.FormError
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import utils.Formats._

import scala.concurrent.{ExecutionContext, Future}
import constants.results.Errors._
import utils.Implicits._

/**
  * @author zyuiop
  */
class OrdersController @Inject()(cc: ControllerComponents, orders: OrdersModel)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def getOrders = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      notAuthenticated.asFuture
    else {
      orders.loadOrders(user.get.id, user.get.hasPerm(Permissions.SEE_ALL_ORDER_TYPES)).map(ords => Ok(
        JsArray(ords.map(ord => Json.toJson(JsonOrder(ord))))
      ))
    }
  }
  }

  def getOrder(orderId: Int) = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      Errors.notAuthenticated.asFuture
    else {
      orders.loadOrder(orderId)
        .map(opt =>
          opt.filter(data => data.order.clientId == user.get.id || user.get.hasPerm(Permissions.VIEW_OTHER_ORDER)))
        .map {
          case Some(data) => Ok(Json.toJson(JsonOrderData(data)))
          case None => NotFound
        }
    }
  }
  }

}

package controllers.orders

import data._
import javax.inject.Inject
import models.{JsonOrder, JsonOrderData, OrdersModel}
import pdi.jwt.JwtSession._
import play.api.data.FormError
import play.api.libs.json.{JsArray, Json}
import play.api.mvc._
import utils.Formats._

import scala.concurrent.{ExecutionContext, Future}


/**
  * @author zyuiop
  */
class OrdersController @Inject()(cc: ControllerComponents, orders: OrdersModel)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def getOrders = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      Future(Unauthorized(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.no_auth_token")))))
    else {
      orders.loadOrders(user.get.id, user.get.hasPerm("admin.see_all")).map(ords => Ok(
        JsArray(ords.map(ord => Json.toJson(JsonOrder(ord))))
      ))
    }
  }
  }

  def getOrder(orderId: Int) = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      Future(Unauthorized(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.no_auth_token")))))
    else {
      orders.loadOrder(orderId)
        .map(opt =>
          opt.filter(data => data.order.clientId == user.get.id || user.get.hasPerm("admin.view_other_order")))
        .map {
          case Some(data) => Ok(Json.toJson(JsonOrderData(data)))
          case None => NotFound
        }
    }
  }
  }

}

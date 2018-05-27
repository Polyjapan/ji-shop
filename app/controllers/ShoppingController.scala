package controllers

import data._
import javax.inject.Inject
import models.{ClientsModel, OrdersModel, ProductsModel}
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import utils.HashHelper
import pdi.jwt.JwtSession._
import pdi.jwt._
import play.api.data.FormError
import services.PolybankingClient

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import utils.Formats._


/**
  * @author zyuiop
  */
class ShoppingController @Inject()(cc: MessagesControllerComponents, orders: OrdersModel, products: ProductsModel, mailerClient: MailerClient, config: Configuration, pb: PolybankingClient)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {

  implicit val eventFormat = Json.format[Event]
  implicit val productFormat = Json.format[Product]

  def homepage: Action[AnyContent] = Action.async { implicit request => {
    products.getProducts.map(data => {
      val common = data.mapValues(_.partition(_.isTicket))
      val tickets = common.mapValues(_._1)
      val goodies = common.mapValues(_._2)

      def remap(map: Map[Event, Seq[Product]]) =
        map.filter(_._2.nonEmpty).map(pair => Json.obj("event" -> pair._1, "items" -> pair._2)).toList

      val json = Json.obj("tickets" -> remap(tickets), "goodies" -> remap(goodies))

      Ok(json)
    })
  }
  }

  def checkout = Action.async(parse.json) { implicit request => {
    val session = request.jwtSession
    val user = session.get("user")
    val itemsOpt = request.body.asOpt[Seq[CheckedOutItem]]

    if (user.isEmpty) {
      Future(Unauthorized(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.no_auth_token")))))
    } else if (itemsOpt.isEmpty || itemsOpt.get.isEmpty) {
      Future(BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.no_requested_item")))))
    } else {
      val items = itemsOpt.get.groupBy(_.itemId)

      if (items.exists(pair => pair._2.size > 1)) {
        Future(BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.multiple_instances_of_single_item")))))
      }

      products.getMergedProducts.map(_.map(p => (p, items.get(p.id.get))).toMap
          .filter(p => p._2.nonEmpty && p._2.get.nonEmpty)
          .mapValues(s => s.get.head)
          .map({
            case (product, coItem) if product.freePrice =>
              if (coItem.itemPrice.isEmpty || coItem.itemPrice.get < product.price)
                (product, coItem.copy(itemPrice = Some(product.price)))
              else (product, coItem)
            case (product, coItem) => (product, coItem.copy(itemPrice = Some(product.price)))
          })
          .mapValues(coItem => coItem.copy(itemPrice = coItem.itemPrice.map(d => math.round(d * 100) / 100D)))
      ).flatMap(v => {
            val ticketsPrice = v.filter(p => p._1.isTicket).values.map(_.itemPrice.get).sum
            val totalPrice = v.values.map(_.itemPrice.get).sum

        orders.createOrder(Order(Option.empty, user.get.as[AuthenticatedUser].id, ticketsPrice, totalPrice)).map((v.values, _, totalPrice))
      }).flatMap{
        case (list, orderId, totalPrice) =>
          val items = list.flatMap(coItem =>
            for (i <- 1 to coItem.itemAmount)  // Generate as much ordered products as the quantity requested
              yield OrderedProduct(Option.empty, coItem.itemId, orderId, coItem.itemPrice.get)
          )

          orders.orderProducts(items).flatMap {
            case Some(v) if v >= items.size =>
              // TODO: the client should check that the returned ordered list contains the same items that the one requested
              pb.startPayment(totalPrice, orderId, list).map {
                case (true, url) =>
                  Ok(Json.obj("ordered" -> list, "success" -> true, "redirect" -> url))
                case (false, err) =>
                  InternalServerError(Json.obj("success" -> false, "errors" -> Seq(FormError("", s"error.polybanking.$err"))))
              }
            case _ =>
              Future(InternalServerError(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.db_error")))))
          }
      }.recover{
        case _: Throwable =>
          InternalServerError(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.exception"))))
      }
    }
  }}

}

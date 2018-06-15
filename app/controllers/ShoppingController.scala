package controllers

import data._
import exceptions.OutOfStockException
import javax.inject.Inject
import models.OrdersModel.TicketBarCode
import models.{JsonOrder, JsonOrderData, OrdersModel, ProductsModel}
import pdi.jwt.JwtSession._
import play.api.Configuration
import play.api.data.FormError
import play.api.i18n.I18nSupport
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.libs.mailer.{AttachmentData, Email, MailerClient}
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import services.PolybankingClient.CorrectIpn
import services.{PolybankingClient, TicketGenerator}
import utils.Formats._

import scala.concurrent.{ExecutionContext, Future}


/**
  * @author zyuiop
  */
class ShoppingController @Inject()(cc: MessagesControllerComponents, pdfGen: TicketGenerator, orders: OrdersModel, products: ProductsModel, mailerClient: MailerClient, config: Configuration, pb: PolybankingClient)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {


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

  def test = Action {
    Ok(pdfGen.genPdf(TicketBarCode(
      data.Product(None, "Name", 0, "descript", "Lorem ipsum", 0, 0, true, true),
      "912345678901234",
      data.Event(None, "Japan Impact 27", "EPFL Mars - Mars", true)
    ))._2).as("application/pdf")

  }

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
  }}

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
  }}

  /**
    * Get the PDF ticket for a given barcode
    * @param barCode the barcode searched
    * @return the pdf ticket
    */
  def getTicket(barCode: String) = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      Future(Unauthorized(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.no_auth_token")))))
    else {
      orders.findBarcode(barCode) map {
        case None =>
          NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.ticket_not_found"))))
        case Some((code, client: Client)) =>
          if (client.id.get != user.get.id && !user.get.hasPerm("admin.view_other_ticket"))
            // Return the same error as if the ticket didn't exist
            // It avoids leaking information about whether or not a ticket exists
            NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.ticket_not_found"))))
          else {
            // Generate the PDF
            Ok(pdfGen.genPdf(code)._2).as("application/pdf")
          }
      }
    }
  }}

  def ipn: Action[Map[String, Seq[String]]] = Action.async(parse.formUrlEncoded) { implicit request => {
    pb.checkIpn(request.body) match {
      case CorrectIpn(valid: Boolean, order: Int) =>
        println(s"Processing IPN request for order $order. PostFinance status is $valid")
        if (!valid) Future(BadRequest)
        else orders.acceptOrder(order).map {
          case (Seq(), _) => NotFound
          case (oldSeq, client) =>
            val attachments =
              oldSeq.map(pdfGen.genPdf).map(p => AttachmentData(p._1, p._2, "application/pdf"))

            mailerClient.send(Email(
              request.messages("shopping.tickets.email_title"),
              request.messages("shopping.tickets.email_from") + " <ticket@japan-impact.ch>",
              Seq(client.email),
              bodyText = Some(request.messages("shopping.tickets.email_text")),
              attachments = attachments
            ))

            Ok
          case _ => BadRequest
        }
      case a@_ => Future(BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", a.toString)))))
    }

  }
  }

  def checkout: Action[JsValue] = Action.async(parse.json) { implicit request => {
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

      products.getMergedProducts.map(_ // get all the products in database
        .map(p => (p, items.get(p.id.get))).toMap // map the DB product to the one in the order
        .filter(p => p._2.nonEmpty && p._2.get.nonEmpty) // remove the DB products that are not in the order
        .mapValues(s => s.get.head) // there is only a single item in each sequence
        .map({ // Check the item prices
          case (product, coItem) if product.freePrice =>
            if (coItem.itemPrice.isEmpty || coItem.itemPrice.get < product.price)
              (product, coItem.copy(itemPrice = Some(product.price)))
            else (product, coItem)
          case (product, coItem) => (product, coItem.copy(itemPrice = Some(product.price)))
        })
        .mapValues(coItem => coItem.copy(itemPrice = coItem.itemPrice.map(d => math.round(d * 100) / 100D))) // round the prices to 2 decimals
      ).map(m => {
        val byId = m.map(_._2.itemId).toSet // Create a set of all item ids

        if (items.forall(i => byId(i._1))) { // Check that all items in the order correspond to available items
          val oos = m.filter(p => p._1.maxItems >= 0 && p._1.maxItems < p._2.itemAmount).keys // Get all the out of stock items
          if (oos.isEmpty) m // Check that all the items are available
          else throw new OutOfStockException(oos)
        } else throw new NoSuchElementException
      }).flatMap(v => {

        def sumPrice(list: Iterable[CheckedOutItem]) = list.map(p => p.itemPrice.get * p.itemAmount).sum

        val ticketsPrice = sumPrice(v.filter(p => p._1.isTicket).values)
        val totalPrice = sumPrice(v.values)

        orders.createOrder(Order(Option.empty, user.get.as[AuthenticatedUser].id, ticketsPrice, totalPrice)).map((v.values, _, totalPrice))
      }).flatMap {
        case (list, orderId, totalPrice) =>
          val items = list.flatMap(coItem =>
            for (i <- 1 to coItem.itemAmount) // Generate as much ordered products as the quantity requested
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
      }.recover {
        case OutOfStockException(items) =>
          NotFound(Json.obj("success" -> false, "errors" ->
            Seq(
              FormError("", "error.item_out_of_stock",
                items.map(it => Json.obj("itemId" -> it.id, "itemName" -> it.name)).toSeq) // return a list of items missing in the error
            )))
        case _: NoSuchElementException =>
          NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.missing_item"))))
        case _: Throwable =>
          InternalServerError(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.exception"))))
      }
    }
  }
  }

}

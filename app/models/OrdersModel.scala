package models

import java.security.SecureRandom
import java.sql.{SQLIntegrityConstraintViolationException, Timestamp}

import data.{Order, OrderedProduct, Ticket}
import javax.inject.Inject
import models.OrdersModel.{GeneratedBarCode, OrderBarCode, TicketBarCode}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import slick.jdbc.MySQLProfile
import utils.Barcodes
import utils.Barcodes.{BarcodeType, OrderCode, ProductCode}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class OrdersModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  def createOrder(order: Order): Future[Int] = db.run((orders returning orders.map(_.id)) += order)

  def orderProducts(ordered: Iterable[OrderedProduct]): Future[Option[Int]] = db.run(orderedProducts ++= ordered)

  private val productJoin = (orderedProducts join products on (_.productId === _.id) join events on (_._2.eventId === _.id)).map { case ((p1, p2), p3) => (p1, p2, p3) }
  private val orderJoin = orders join clients on (_.clientId === _.id)


  type TicketFormat = BigInt => String
  private val numeric: TicketFormat = _.toString(10).takeRight(14)

  // 10 alphanum characters still produces a barcode with fine size. More makes it too big
  private val alphaNumeric: TicketFormat = _.toString(36).takeRight(10).toUpperCase

  private val usedFormat = numeric

  private def barcodeGen(barcodeType: BarcodeType): String = {
    val bytes = new Array[Byte](8)
    new SecureRandom().nextBytes(bytes)

    barcodeType.getId + usedFormat(BigInt(bytes))
  }

  def loadOrders(userId: Int, isAdmin: Boolean): Future[Seq[data.Order]] = {
    db.run(clients.filter(_.id === userId)
      .join(orders)
      .on(_.id === _.clientId)
      .map(_._2)
      .result
      .map(_.filter(_.source == data.Web || isAdmin)))
  }

  def loadOrder(orderId: Int): Future[Option[OrderData]] = {
    val oPTicketsJoin = orderedProductTickets join tickets on (_.ticketId === _.id)
    val oTicketsJoin = orderTickets join tickets on (_.ticketId === _.id)

    val req = orders.filter(_.id === orderId)
      .join(orderedProducts).on(_.id === _.orderId)
      .join(products).on(_._2.productId === _.id)
      .map { case ((ord, op), p) => (ord, op, p) }
      .joinLeft(oPTicketsJoin).on(_._2.id === _._1.orderedProductId)
      .joinLeft(oTicketsJoin).on(_._1._1.id === _._1.orderId)
      .map { case (((ord, op, p), productCode), orderCode) => (ord, op, p, productCode, orderCode) }
      .result
      .map(seq => {
        val productMap = seq
          .map{ case (_, o, p, pc, _) => (o, p, pc.map(_._2.barCode)) } // Keep only (orderedProduct, product, barcode)
          .groupBy(p => (p._1.productId, p._1.paidPrice)) // Group the data by (orderedProduct, product)
          .map(pair => ((pair._2.head._1, pair._2.head._2), pair._2))
          .mapValues(seq => (seq.length, seq.map(_._3).filter(_.nonEmpty).map(_.get))) // compute the amt of items then the list of codes

        seq.headOption.map { case (ord, _, _, _, orderCode) =>
          OrderData(ord, productMap, orderCode.map(_._2.barCode))
        }
      })

    db.run(req)
  }

  def findBarcode(barcode: String): Future[Option[(GeneratedBarCode, data.Client)]] = {
    val req = tickets.filter(_.barCode === barcode).map(_.id).result.map(seq => seq.head).flatMap(barcodeId => {
      Barcodes.parseCode(barcode) match {
        case ProductCode => findProduct(barcode, barcodeId)
        case OrderCode => findOrder(barcode, barcodeId)
        case _ => DBIO.failed(new UnknownError)
      }


    })

    db.run(req).recover {
      case e: UnknownError => None
      case e: UnsupportedOperationException => None // no result (empty.head)
    }
  }

  private def findOrder(barcode: String, barcodeId: Int): DBIOAction[Option[(GeneratedBarCode, data.Client)], NoStream, Effect.Read] = {
    val join =
      orderTickets filter (_.ticketId === barcodeId) join
        orders on (_.orderId === _.id) map (_._2) join
        clients on (_.clientId === _.id) join
        productJoin on (_._1.id === _._1.orderId) map { case ((a, b), (c, d, e)) => (a.id, d, e, b)}

    join.result.map(seq => {
      val products = seq.map(_._2).groupBy(p => p).mapValues(_.size)

      seq.headOption.map {
        case (orderId, _, event, client) => (OrderBarCode(orderId, products, barcode, event), client)
      }
    })
  }

  private def findProduct(barcode: String, barcodeId: Int): DBIOAction[Option[(GeneratedBarCode, data.Client)], NoStream, Effect.Read] = {
    val join =
      orderedProductTickets filter (_.ticketId === barcodeId) join
        productJoin on (_.orderedProductId === _._1.id) join
        orders on (_._2._1.orderId === _.id) join
        clients on (_._2.clientId === _.id) map { case (((_, (_, p, e)), _), client) => (p, e, client) }

    join.result.map(_ map { case (p, e, client) => (TicketBarCode(p, barcode, e), client) } headOption)
  }

  def acceptOrder(order: Int): Future[(Seq[GeneratedBarCode], data.Client)] = {
    // This query updates the confirm time of the order
    val q0 = orders
      .filter(_.id === order)
      .map(_.paymentConfirmed)
      .filter(_.isEmpty)
      .update(Some(new Timestamp(System.currentTimeMillis())))

    // This query selects the tickets in the command
    val q1 = productJoin.filter(_._1.orderId === order).filter(_._2.isTicket).result

    // This query insert tickets for the tickets and return their barcodes
    // Due to DB constraints, a given ordered_product cannot have more than one ticket
    // Thanks to atomic execution, if a given order has already been IPN-ed, its tickets won't be regenerated
    val qq1: DBIOAction[Seq[GeneratedBarCode], _, _] = (q1 flatMap {
      a =>
        DBIO.sequence(a.map(product => (product, Ticket(Option.empty, barcodeGen(ProductCode)))).map(pair =>
          // Add a ticket in the database and get its id back
          ((tickets returning tickets.map(_.id)) += pair._2)
            // Create a pair (product, ticket)
            .map(ticketId => (pair._1._1.id.get, ticketId))
            // Insert that pair
            .flatMap(pair => orderedProductTickets += pair)
            .flatMap(_ => DBIO.successful(TicketBarCode(pair._1._2, pair._2.barCode, pair._1._3))))
        )
    }).transactionally // Do all this atomically to prevent the creation of thousands of useless tickets

    // This query selects the products in the command that are not tickets
    val q2 = productJoin.filter(_._1.orderId === order).filterNot(_._2.isTicket).result

    // This query inserts a single ticket for the order and return its barcode
    val qq2 = (q2 filter (_.nonEmpty) flatMap (r => {

      val event = r.headOption.map(_._3).getOrElse(data.Event(None, "unknown_event", "unknown_location", visible = false))
      val products = r.map(_._2).groupBy(p => p).mapValues(_.size)
      // If we have items:
      // Create a ticket
      val ticket = Ticket(Option.empty, barcodeGen(OrderCode))
      ((tickets returning tickets.map(_.id)) += ticket)
        // Link it to the order
        .flatMap(ticketId => orderTickets += (order, ticketId))
        .flatMap(_ => DBIO.successful(OrderBarCode(order, products, ticket.barCode, event)))
    })).transactionally

    val q3 = orderJoin.filter(_._1.id === order).take(1).result

    val qq4 = productJoin.filter(_._1.orderId === order) // Get all products in the order
        .map(_._2).filter(_.maxItems > 0).result // if their maxItems value is > 0...

    val q4 = qq4.flatMap(res =>
          DBIO.sequence(
            res.map(p => products.filter(_.id === p.id).map(_.maxItems).update(p.maxItems - 1))
          ))  // decrease the maxItems value

    val result = qq1 flatMap
      (seq => qq2.flatMap(code => DBIO.successful(seq :+ code))) flatMap // get the order code
      (seq => q3.flatMap(cli => DBIO.successful((seq, cli.head._2)))) flatMap // get the client
      (res => q4.flatMap(_ => DBIO.successful(res))) // execute the 4th request, ignoring its result


    db.run((q0 andThen result).transactionally).recover {
      case e: SQLIntegrityConstraintViolationException =>
        println("Duplicate IPN request for " + order + ", returning empty result")
        (Seq(), null)
    }
  }
}

object OrdersModel {

  /**
    * A trait representing a barcode
    */
  sealed trait GeneratedBarCode

  /**
    * This class represents a barcode bound to a ticket (i.e. a product that allow you to enter the event)
    *
    * @param product the product representing the ticket (here: the product) (useful to find the template for the PDF generation)
    * @param barcode the actual barcode for the ticket
    */
  case class TicketBarCode(product: data.Product, barcode: String, event: data.Event) extends GeneratedBarCode

  /**
    * This class represents a barcode bound to an order<br>
    * Such barcodes are generated only for orders that contain at least one non-ticket item (i.e. goodies)
    *
    * @param order   the order this ticket/barcode is bound to
    * @param barcode the actual barcode for the ticket
    */
  case class OrderBarCode(order: Int, products: Map[data.Product, Int], barcode: String, event: data.Event) extends GeneratedBarCode

}

case class OrderData(order: data.Order, products: Map[(OrderedProduct, data.Product), (Int, Seq[String])], orderCode: Option[String])

case class JsonOrder(id: Int, price: Double, paymentConfirmed: Boolean, createdAt: Long, source: String)

case object JsonOrder {
  def apply(order: data.Order): JsonOrder = order match {
    case Order(Some(id), _, _, price, paymentConfirmed, Some(enterDate), source) =>
      JsonOrder(id, price, paymentConfirmed.isDefined, enterDate.getTime, source.toString)
  }

  implicit val format = Json.format[JsonOrder]
}

case class JsonOrderedProduct(product: data.Product, paidPrice: Double, amount: Int, codes: Seq[String])

case object JsonOrderedProduct {
  def apply(pair: ((OrderedProduct, data.Product), (Int, Seq[String]))): JsonOrderedProduct = pair match {
    case ((op, product), (amt, codes)) =>
      JsonOrderedProduct(product, op.paidPrice, amt, codes)
  }

  implicit val format = Json.format[JsonOrderedProduct]
}

case class JsonOrderData(order: JsonOrder, products: Seq[JsonOrderedProduct], orderCode: Option[String])

case object JsonOrderData {
  def apply(data: OrderData): JsonOrderData = data match {
    case OrderData(order, pdts, code) =>
      JsonOrderData(JsonOrder(order), pdts.map(JsonOrderedProduct.apply).toSeq, code)
  }

  implicit val format = Json.format[JsonOrderData]
}
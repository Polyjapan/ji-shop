package models

import java.security.SecureRandom
import java.sql.{SQLIntegrityConstraintViolationException, Timestamp}

import data.{AuthenticatedUser, CheckedOutItem, Gift, OnSite, Order, OrderedProduct, Product, Source, Ticket}
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

  /**
    * Insert an order in the database
    *
    * @param order the order to insert, with the id field set to `None`
    * @return the number of orders inserted (should be == 1)
    */
  def createOrder(order: Order): Future[Int] = db.run((orders returning orders.map(_.id)) += order)

  /**
    * Insert multiple ordered products at once. Their orderId MUST be set to a previously inserted order.
    *
    * @param ordered the ordered products to insert
    * @return the number of elements inserted (should be == `ordered.size`)
    */
  def orderProducts(ordered: Iterable[OrderedProduct]): Future[Option[Int]] = db.run(orderedProducts ++= ordered)

  /**
    * List of ordered products, associated with the product and the event in which this product appears
    * ordered_products JOIN products JOIN events
    */
  private val productJoin = (orderedProducts join products on (_.productId === _.id) join events on (_._2.eventId === _.id)).map { case ((p1, p2), p3) => (p1, p2, p3) }

  /**
    * List of orders and the client who made them
    * orders JOIN clients
    */
  private val orderJoin = orders join clients on (_.clientId === _.id)

  /**
    * Defines a style of barcode
    */
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

  /**
    * Read all orders made by a given user. If the user is not admin, only the orders with source == `Web` will be
    * returned. If the user is admin, all the orders will be returned.
    *
    * @param userId  the id of the user for whom we want to retrieve the orders
    * @param isAdmin whether or not the user is admin
    * @return a future, containing a sequence of orders
    */
  def loadOrders(userId: Int, isAdmin: Boolean): Future[Seq[data.Order]] = {
    db.run(clients.filter(_.id === userId)
      .join(orders)
      .on(_.id === _.clientId)
      .map { case (_, order) => order }
      .result
      .map(_.filter(_.source == data.Web || isAdmin)))
  }

  /**
    * Dump a whole event
    * @param event the id of the event to dump
    * @param date the fnac date of the event (YYYYMMDD HH :MM)
    * @return the dump of the event, as list of semicolon separated values
    */
  def dumpEvent(event: Int, date: String): Future[List[String]] = {
    val join =
      productJoin
        .join(orderedProductTickets).on(_._1.id === _.orderedProductId)
        .join(tickets).on(_._2.ticketId === _.id)
        .map { case (((ordered, product, ev), _), ticket) => (ev, product, ordered, ticket)}


    db.run(join.filter(_._1.id === event).filter(_._2.isTicket === true).result).map(
      seq => "Evenement;Representation;Code Barre;Tarif;Prix" :: seq.map( {
        case (ev, product, ordered, ticket) =>
          ev.name + ";" + date + ";" + ticket.barCode + ";" + product.name + ";" + ordered.paidPrice
      }).toList)
  }

  def getOrder(orderId: Int): Future[Option[Order]] = {
    db.run(orders.filter(_.id === orderId).result.headOption)
  }

  def insertProducts(list: Iterable[CheckedOutItem], orderId: Int): Future[Boolean] = {
    // Create a list of [[OrderedProduct]] to insert
    val items = list.flatMap(coItem =>
      for (i <- 1 to coItem.itemAmount) // Generate as much ordered products as the quantity requested
        yield OrderedProduct(Option.empty, coItem.itemId, orderId, coItem.itemPrice.get)
    )

    orderProducts(items).map {
      case Some(num) => num >= items.size
      case None => false
    }
  }

  /**
    * Read an order by its id and return it. The caller should check that the user requesting the order has indeed the
    * right to read it.
    *
    * @param orderId the orderId to look for
    * @return a future, containing an optional OrderData
    */
  def loadOrder(orderId: Int): Future[Option[OrderData]] = {
    // orderedProductsTickets JOIN tickets
    val oPTicketsJoin = orderedProductTickets join tickets on (_.ticketId === _.id)
    // orderTickets JOIN tickets
    val oTicketsJoin = orderTickets join tickets on (_.ticketId === _.id)

    val req = orders.filter(_.id === orderId) // find the order
      .join(orderedProducts).on(_.id === _.orderId) // join it to its orderedProducts
      .join(products).on(_._2.productId === _.id) // join them to their corresponding product
      .map { case ((ord, op), p) => (ord, op, p) }
      .joinLeft(oPTicketsJoin).on(_._2.id === _._1.orderedProductId) // join the products with their potential barcode
      .joinLeft(oTicketsJoin).on(_._1._1.id === _._1.orderId) // join the order with its potential barcode
      .map { case (((ord, op, p), productCode), orderCode) => (ord, op, p, productCode, orderCode) }
      .result
      .map(seq => {
        val productMap = seq
          .map { case (_, o, p, pc, _) => (o, p, pc.map(_._2.barCode)) } // Keep only (orderedProduct, product, barcode)
          .groupBy(p => (p._1.productId, p._1.paidPrice)) // Group the data by (orderedProduct, product)
          .map(pair => ((pair._2.head._1, pair._2.head._2), pair._2))
          .mapValues(seq => (seq.length, seq.map(_._3).filter(_.nonEmpty).map(_.get))) // compute the amt of items then the list of codes

        seq.headOption.map { case (ord, _, _, _, orderCode) =>
          OrderData(ord, productMap, orderCode.map(_._2.barCode))
        }
      })

    db.run(req)
  }

  /**
    * Inserts in the database all barcodes from an imported order
    * @param input an iterable of ordered products to be inserted with the barcode they are supposed to have
    */
  def fillImportedOrder(input: Iterable[(OrderedProduct, String)]): Future[Iterable[Int]] = {
    val seq = input.map({ case (orderedProduct, barcode) =>
      (orderedProducts.returning(orderedProducts.map(_.id)) += orderedProduct)
        .flatMap(orderedProductId => (tickets.returning(tickets.map(_.id)) += Ticket(None, barcode)).map(ticketId => (orderedProductId, ticketId)))
        .flatMap(pair => orderedProductTickets += pair)
    })


    // I wanted to do it in only 3 requests but sadly it seems hard to do because we can only return the id from the insert command
    // Let's burn the database with 3 * n requests then :)

    db.run(DBIO.sequence(seq).transactionally)
  }

  /**
    * Find a given barcode and return it, along with the client that created it
    *
    * @param barcode the barcode to look for
    * @return an optional triple of the barcode data, the client that created it and the id of the barcode in the database
    */
  def findBarcode(barcode: String): Future[Option[(GeneratedBarCode, data.Client, Int)]] = {
    val req = tickets.filter(_.barCode === barcode).map(_.id).result.map(seq => seq.head).flatMap(barcodeId => {
      Barcodes.parseCode(barcode) match {
        case ProductCode => findProduct(barcode, barcodeId)
        case OrderCode => findOrder(barcode, barcodeId)
        case _ =>
          // We try to find a product then an order
          // Some barcodes (like the one imported from resellers) might not follow the format and therefore not be
          // recognized
          findProduct(barcode, barcodeId).flatMap(result => {
            if (result.isDefined) DBIO.successful(result)
            else findOrder(barcode, barcodeId)
          })
      }
    })

    db.run(req).recover {
      case e: UnknownError => None
      case e: UnsupportedOperationException => None // no result (empty.head)
    }
  }

  private def findOrder(barcode: String, barcodeId: Int): DBIOAction[Option[(GeneratedBarCode, data.Client, Int)], NoStream, Effect.Read] = {
    val join =
      orderTickets filter (_.ticketId === barcodeId) join // find the orderTicket by ticketId
        orders on (_.orderId === _.id) map (_._2) join // find the order by its ticket
        clients on (_.clientId === _.id) join // find the client who made the order
        productJoin on (_._1.id === _._1.orderId) map { // find products in the order
          case ((order, client), (_, product, event)) => (order.id, product, event, client) }

    join.result.map(seq => {
      val products = seq.map{ case (_, product, _, _) => product }.groupBy(p => p).mapValues(_.size)

      seq.headOption.map {
        case (orderId, _, event, client) => (OrderBarCode(orderId, products, barcode, event), client, barcodeId)
      }
    })
  }

  private def findProduct(barcode: String, barcodeId: Int): DBIOAction[Option[(GeneratedBarCode, data.Client, Int)], NoStream, Effect.Read] = {
    val join =
      orderedProductTickets filter (_.ticketId === barcodeId) join // find the orderedProductTicket by ticketId
        productJoin on (_.orderedProductId === _._1.id) join // find the product by its ticket
        orders on (_._2._1.orderId === _.id) join // find the order corresponding to the products
        clients on (_._2.clientId === _.id) map { // find the client who made the order
          case (((_, (_, p, e)), _), client) => (p, e, client) } // keep only product, event and client

    join.result.map(_ map { case (p, e, client) => (TicketBarCode(p, barcode, e), client, barcodeId) } headOption)
  }

  /**
    * Mark an order as paid
    * @param order the order to mark as paid
    * @return a future, containing 1 or an error
    */
  def markAsPaid(order: Int): Future[Int] = {
    db.run(
      orders
        .filter(_.id === order)
        .map(_.paymentConfirmed)
        .update(Some(new Timestamp(System.currentTimeMillis())))
        .filter(r => r == 1) // ensure we updated only one item
    )
  }

  /**
    * Create an order, then insert it in the database, and return its id as well as the total order price
    *
    * @param map  the sanitized & checked map of the order
    * @param user the user making the request
    * @param source the source of the order
    * @return a future holding all the [[CheckedOutItem]], as well as the inserted order ID and the total price
    */
  def postOrder(user: AuthenticatedUser, map: Map[Product, Seq[CheckedOutItem]], source: Source): Future[(Iterable[CheckedOutItem], Int, Double)] = {
    def sumPrice(list: Iterable[CheckedOutItem]) = list.map(p => p.itemPrice.get * p.itemAmount).sum

    if (source == data.Reseller)
      throw new IllegalArgumentException("Order source cannot be Reseller")

    val ticketsPrice = sumPrice(map.filter{ case (product, _) => product.isTicket }.values.flatten)
    val totalPrice = sumPrice(map.values.flatten)

    val order =
      if (source == Gift) Order(Option.empty, user.id, 0D, 0D, source = Gift)
      else Order(Option.empty, user.id, ticketsPrice, totalPrice, source = source)

    createOrder(order).map((map.values.flatten, _, totalPrice))
  }

  def setOrderAccepted(order: Int) = {
    db.run(orders
      .filter(_.id === order)
      .map(_.paymentConfirmed)
      .filter(_.isEmpty)
      .update(Some(new Timestamp(System.currentTimeMillis())))
      .flatMap(r => if (r >= 1) DBIO.successful(Unit) else DBIO.failed(new IllegalStateException())));
  }

  /**
    * Accept an order by its id, generating barcodes and returning them.
    *
    * @param order the order to accept
    * @return a future holding the barcodes as well as the client to which they should be sent
    */
  def acceptOrder(order: Int): Future[(Seq[GeneratedBarCode], data.Client)] = {
    // This query updates the confirm time of the order
    val updateConfirmTimeQuery = orders
      .filter(_.id === order)
      .map(_.paymentConfirmed)
      .filter(_.isEmpty)
      .update(Some(new Timestamp(System.currentTimeMillis())))
      .flatMap(r => if (r >= 1) DBIO.successful(Unit) else DBIO.failed(new IllegalStateException()))

    // This query selects the tickets in the command
    val orderedTicketsQuery = productJoin
      .filter { case (orderedProduct, product, _) => orderedProduct.orderId === order && product.isTicket }
      .result

    // This query insert tickets for the tickets and return their barcodes
    // Due to DB constraints, a given ordered_product cannot have more than one ticket
    // Thanks to atomic execution, if a given order has already been IPN-ed, its tickets won't be regenerated
    val orderedTicketsBarcodesQuery: DBIOAction[Seq[GeneratedBarCode], _, _] = (orderedTicketsQuery flatMap {
      result =>
        DBIO.sequence(result.map(product => (product, Ticket(Option.empty, barcodeGen(ProductCode)))).map {
          case ((orderedProduct, product, event), ticket) =>
            // Add a ticket in the database and get its id back
            ((tickets returning tickets.map(_.id)) += ticket)
              // Create a pair (orderedProduct, ticket)
              .map(ticketId => (orderedProduct.id.get, ticketId))
              // Insert that pair
              .flatMap(pair => orderedProductTickets += pair)
              // Map the result to return a TicketBarCode object
              .flatMap(_ => DBIO.successful(TicketBarCode(product, ticket.barCode, event)))
        }
        )
    }).transactionally // Do all this atomically to prevent the creation of thousands of useless tickets

    // This query selects the products in the command that are not tickets
    val otherProducts = productJoin
      .filter { case (orderedProduct, product, _) => orderedProduct.orderId === order && !product.isTicket }
      .result

    // This query inserts a single ticket for the order and return its barcode
    val orderBarcodeQuery = (otherProducts flatMap (r => {
      if (r.nonEmpty) {

        val event = r.headOption.map { case (_, _, pairEvent) => pairEvent }.getOrElse(data.Event(None, "unknown_event", "unknown_location", visible = false))
        val products = r.map { case (_, product, _) => product }.groupBy(p => p).mapValues(_.size)
        // If we have items:
        // Create a ticket
        val ticket = Ticket(Option.empty, barcodeGen(OrderCode))
        ((tickets returning tickets.map(_.id)) += ticket)
          // Link it to the order
          .flatMap(ticketId => orderTickets += (order, ticketId))
          .flatMap(_ => DBIO.successful(OrderBarCode(order, products, ticket.barCode, event)))
      } else {
        DBIO.successful(null)
      }
    })).transactionally

    val orderQuery = orderJoin.filter(_._1.id === order).take(1).result

    val allProductsQuery = productJoin
      .filter { case (orderedProduct, _, _) => orderedProduct.orderId === order } // Get all products in the order
      .map { case (_, product, _) => product } // keep only the product itself
      .filter(_.maxItems > 0).result // if their maxItems value is > 0...

    val updateRemainingStockQuery = allProductsQuery.flatMap(res =>
      DBIO.sequence(
        res.map(p => products.filter(_.id === p.id).map(_.maxItems).update(p.maxItems - 1))
      )) // decrease the maxItems value

    val computeResult = orderedTicketsBarcodesQuery flatMap
      (seq => orderBarcodeQuery.flatMap(code => {
        if (code != null) DBIO.successful(seq :+ code)
        else DBIO.successful(seq)
      })) flatMap // get the order code
      (seq => orderQuery.flatMap(cli => DBIO.successful((seq, cli.head._2)))) flatMap // get the client
      (res => updateRemainingStockQuery.flatMap(_ => DBIO.successful(res))) // execute the 4th request, ignoring its result


    db.run((updateConfirmTimeQuery andThen computeResult).transactionally).recover {
      case _: SQLIntegrityConstraintViolationException | _: IllegalStateException =>
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

/**
  * A convenience return type for the [[OrdersModel.loadOrder]] method
  *
  * @param order     the order data
  * @param products  a map of products with the quantity and a sequence of barcodes, if any
  * @param orderCode the barcode of the order, if any
  */
case class OrderData(order: data.Order, products: Map[(OrderedProduct, data.Product), (Int, Seq[String])], orderCode: Option[String])

/**
  * A description of an order that can be mapped to JSon
  *
  * @param id               the id of the order
  * @param price            the total cart price of the order
  * @param paymentConfirmed whether or not the payment has been received
  * @param createdAt        the time (ms) at which this order was created
  * @param source           where this order comes from ([[data.Source.toString]])
  */
case class JsonOrder(id: Int, price: Double, paymentConfirmed: Boolean, createdAt: Long, source: String)

case object JsonOrder {
  /**
    * Convert a database order to a [[JsonOrder]]. The given order MUST have already been inserted.
    *
    * @param order the order to convert
    * @return a [[JsonOrder]]
    */
  def apply(order: data.Order): JsonOrder = order match {
    case Order(Some(id), _, _, price, paymentConfirmed, Some(enterDate), source) =>
      JsonOrder(id, price, paymentConfirmed.isDefined, enterDate.getTime, source.toString)
  }

  implicit val format = Json.format[JsonOrder]
}

/**
  * A description of an ordered product that can be mapped to JSON
  *
  * @param product   the db description of the product
  * @param paidPrice the amount paid for this product
  * @param amount    the quantity of this product ordered
  * @param codes     a sequence of barcodes associated with this product, of length <= amount
  */
case class JsonOrderedProduct(product: data.Product, paidPrice: Double, amount: Int, codes: Seq[String])

case object JsonOrderedProduct {
  def apply(pair: ((OrderedProduct, data.Product), (Int, Seq[String]))): JsonOrderedProduct = pair match {
    case ((op, product), (amt, codes)) =>
      JsonOrderedProduct(product, op.paidPrice, amt, codes)
  }

  implicit val format = Json.format[JsonOrderedProduct]
}

/**
  * A JSON type representing an order with all its products
  *
  * @param order     the JSON representation of the order itself
  * @param products  the products in the order
  * @param orderCode the barcode of the order, if any
  */
case class JsonOrderData(order: JsonOrder, products: Seq[JsonOrderedProduct], orderCode: Option[String])

case object JsonOrderData {
  def apply(data: OrderData): JsonOrderData = data match {
    case OrderData(order, pdts, code) =>
      JsonOrderData(JsonOrder(order), pdts.map(JsonOrderedProduct.apply).toSeq, code)
  }

  implicit val format = Json.format[JsonOrderData]
}
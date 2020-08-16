package models

import java.security.SecureRandom
import java.sql.{SQLIntegrityConstraintViolationException, Timestamp}

import data.{AuthenticatedUser, CheckedOutItem, ClaimedTicket, Client, Gift, Order, OrderLog, OrderedProduct, Product, Source, Ticket}
import javax.inject.Inject
import models.OrdersModel.{GeneratedBarCode, OrderBarCode, TicketBarCode}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.Json
import slick.jdbc.MySQLProfile
import utils.Barcodes
import utils.Barcodes.{BarcodeType, OrderCode, ProductCode}

import scala.language.postfixOps

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
  private val orderJoin = (orders filterNot (_.removed)) join clients on (_.clientId === _.id)
  private val allOrderJoin = orders join clients on (_.clientId === _.id)

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

    s"${barcodeType.getId}${usedFormat(BigInt(bytes))}"
  }

  def setOrderRemoved(orderId: Int, removed: Boolean) = {
    val orderReq = orders
      .filter(_.id === orderId)
      .map(_.removed)
      .update(removed)

    // Removing an order is, in itself, enough to disable all its tickets, as the requests that
    // fetch the tickets always join on the orders as well.
    // However, we still want to tag the tickets as removed, as we can more easily exclude them in scan
    // statistics, and because it makes them disabled even in endpoints that don't join on the orders

    val orderTicketReq = orderTickets.filter(_.orderId === orderId)
      .map(_.ticketId)
      .result
      .headOption
      .flatMap {
        case Some(ticketId) => tickets
          .filter(_.id === ticketId)
          .map(_.removed)
          .update(removed)
        case None => DBIO.successful(0)
      }

    val ticketsReq = orderedProducts.filter(_.orderId === orderId)
      .join(orderedProductTickets).on(_.id === _.orderedProductId).map(_._2)
      .map(_.ticketId)
      .result
      .flatMap(ticketIds =>
        DBIO.sequence(
          ticketIds.map(tid =>
            tickets
              .filter(_.id === tid)
              .map(_.removed)
              .update(removed)
          )))

    db.run(orderReq andThen orderTicketReq andThen ticketsReq)
  }

  def insertLog(orderId: Int, name: String, details: String = null, accepted: Boolean = false): Future[Int] =
    db.run(orderLogs += OrderLog(None, orderId, null, name, Option.apply(details), accepted))

  /**
    * Read all orders made by a given user. If the user is not admin, only the orders with source == `Web` will be
    * returned. If the user is admin, all the orders will be returned.
    *
    * @param userId  the id of the user for whom we want to retrieve the orders
    * @param isAdmin whether or not the user is admin
    * @return a future, containing a sequence of orders
    */
  def loadOrders(userId: Int, isAdmin: Boolean): Future[Seq[data.Order]] = {
    val ordersTable = if (isAdmin) orders else orders.filterNot(_.removed)

    db.run(clients.filter(_.id === userId)
      .join(ordersTable)
      .on(_.id === _.clientId)
      .map { case (_, order) => order }
      .result
      .map(_.filter(_.source == data.Web || isAdmin)))
  }

  def userFromOrder(order: Int): Future[Client] =
    db.run(allOrderJoin.filter(_._1.id === order).map(_._2).result.head)

  def getPosPaymentLogs(order: Int): Future[Seq[data.PosPaymentLog]] =
    db.run(posPaymentLogs.filter(_.orderId === order).result)

  def getOrderLogs(order: Int): Future[Seq[data.OrderLog]] =
    db.run(orderLogs.filter(_.orderId === order).result)

  /**
    * Get all the orders in a given event.
    *
    * @param eventId the id of the event to look for
    * @return the orders in this event
    */
  def ordersByEvent(eventId: Int, returnRemovedOrders: Boolean = false): Future[Seq[data.Order]] = {
    val ordersTable = (if (returnRemovedOrders) orders else orders.filterNot(_.removed))

    db.run(
      ordersTable
        .join(productJoin).on(_.id === _._1.orderId) // join with products and events
        .filter(_._2._3.id === eventId) // get only this event id
        .map(_._1) // get only the order
        .distinctOn(_.id) // distinct ids
        .result
    )
  }

  /**
    * Dump a whole event
    *
    * @param event the id of the event to dump
    * @param date  the fnac date of the event (YYYYMMDD HH :MM)
    * @return the dump of the event, as list of semicolon separated values
    */
  def dumpEvent(event: Int, date: String): Future[List[String]] = {
    val join =
      productJoin
        .join(orderedProductTickets).on(_._1.id === _.orderedProductId)
        .join(tickets).on(_._2.ticketId === _.id)
        .filterNot(_._2.removed) // Exclude removed tickets
        .map { case (((ordered, product, ev), _), ticket) => (ev, product, ordered, ticket) }


    db.run(join.filter(_._1.id === event).filter(_._2.isTicket === true).result).map(
      seq => "Evenement;Representation;Code Barre;Tarif;Prix" :: seq.map({
        case (ev, product, ordered, ticket) =>
          ev.name + ";" + date + ";" + ticket.barCode + ";" + product.name + ";" + ordered.paidPrice
      }).toList)
  }

  def getOrder(orderId: Int): Future[Option[Order]] = {
    db.run(orders.filter(_.id === orderId).filterNot(_.removed).result.headOption)
  }

  def getOrderDetails(orderId: Int): Future[Option[(Order, Client, data.Event, Seq[(OrderedProduct, Product)])]] = {
    db.run(
      orderJoin.filter(_._1.id === orderId)
        .join(productJoin).on(_._1.id === _._1.orderId)
        .result
    ).map {
      case seq if seq.nonEmpty =>
        val ((order, client), (_, _, event)) = seq.head
        val rest = seq.map(_._2).map { case (op, p, _) => (op, p) }
        Some((order, client, event, rest))
      case _ => None
    }
  }

  def getOrderProducts(orderId: Int): Future[Seq[(OrderedProduct, Product)]] =
    db.run(
      productJoin.filter(_._1.orderId === orderId).map { case (op, p, _) => (op, p) }.result
    )

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

  def getBarcodes(orderId: Int): Future[(Seq[GeneratedBarCode], Option[(data.Client, data.Event, data.Order)])] = {
    db.run(
      orderJoin.filter(_._1.id === orderId)
        .join(productJoin).on(_._1.id === _._1.orderId)
        .joinLeft(orderedProductTickets).on(_._2._1.id === _.orderedProductId)
        .joinLeft(orderTickets).on(_._1._1._1.id === _.orderId)
        .map {
          case ((((order, user), (orderedProduct, product, event)), optProdTicket), optOrderTicket) =>
            (order, orderedProduct, product, event, optProdTicket.map(_.ticketId), optOrderTicket.map(_.ticketId), user)
        }
        //.filter(tuple => tuple._5.isDefined || tuple._6.isDefined)
        .joinLeft(tickets).on((left, right) => left._5 === right.id && !right.removed)
        .joinLeft(tickets).on((left, right) => left._1._6 === right.id && !right.removed)
        .map {
          case (((order, orderedProduct, product, event, _, _, client), optProdTicket), optOrderTicket) =>
            (order, orderedProduct, product, event, optProdTicket.map(_.barCode), optOrderTicket.map(_.barCode), client)
        }
        .result
        .map(seq => {
          val productTickets = seq.filter(_._3.isTicket).filter(_._5.isDefined).map {
            case (_, _, product, event, Some(barcode), _, _) =>
              OrdersModel.TicketBarCode(product, barcode, event)
          }

          val userEventOrder: Option[(Client, data.Event, Order)] = seq.headOption.map {
            case (order, _, _, event, _, _, user) => (user, event, order)
          }

          if (!seq.forall(_._3.isTicket)) {
            // There is an order barcode
            val headTuple = seq.find(p => p._6.isDefined).get
            val products = seq.map { case (_, _, product, _, _, _, _) => product }.filterNot(p => p.isTicket).groupMapReduce(p => p)(l => 1)(_ + _)

            val s = productTickets.:+(OrderBarCode(headTuple._1.id.get, products, headTuple._6.get, headTuple._4))
            (s, userEventOrder)
          } else (productTickets, userEventOrder)
        }


        )
    )
  }

  /**
    * Read an order by its id and return it. The caller should check that the user requesting the order has indeed the
    * right to read it.
    *
    * @param orderId        the orderId to look for
    * @param includeRemoved if true, the removed tickets will be included in the reply
    * @return a future, containing an optional OrderData
    */
  def loadOrder(orderId: Int, includeRemoved: Boolean = false): Future[Option[OrderData]] = {
    // orderedProductsTickets JOIN tickets
    val oPTicketsJoin = orderedProductTickets join tickets on ((left, right) => left.ticketId === right.id && (!right.removed || includeRemoved))
    // orderTickets JOIN tickets
    val oTicketsJoin = orderTickets join tickets on ((left, right) => left.ticketId === right.id && (!right.removed || includeRemoved))

    val req = orders.filter(_.id === orderId).filter(order => !order.removed || includeRemoved) // find the order
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
          .view.mapValues(seq => (seq.length, seq.map(_._3).filter(_.nonEmpty).map(_.get))).toMap // compute the amt of items then the list of codes

        seq.headOption.map { case (ord, _, _, _, orderCode) =>
          OrderData(ord, productMap, orderCode.map(_._2.barCode))
        }
      })

    db.run(req)
  }

  /**
    * Inserts in the database all barcodes from an imported order
    *
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
    val req = tickets
      .filter(_.barCode === barcode) // Get the BarCode corresponding to the scanned barcode string
      .filterNot(_.removed) // Remove the removed barcodes from the order
      .map(_.id) // Only get the id
      .result.map(seq => seq.head) // Only get the first one
      .flatMap(barcodeId => {
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

  /**
    * Gets a list of barcodes and return only the barcodes that actually exist in the database
    */
  def filterBarcodes(barcodes: Seq[String]): Future[Seq[String]] = {
    db.run(tickets.map(_.barCode).filter(_.inSet(barcodes)).result)
  }

  def getTicketValidationStatus(ticketId: Int): Future[Option[(ClaimedTicket, Client)]] = {
    db.run((claimedTickets join clients on (_.claimedBy === _.id)) // we join with the clients so that we can return useful information in the exception (i.e. the name)
      .filter(_._1.ticketId === ticketId)
      .result
      .headOption)
  }

  /**
    * Find the order a given barcode belongs to
    *
    * @param barcode the barcode to look for
    * @return a tuple (ticketData, orderId) if the barcode exists, None if not
    */
  def findOrderByBarcode(barcode: String): Future[Option[(Ticket, Int)]] = {
    val req = tickets
      .filter(_.barCode === barcode) // Get the BarCode corresponding to the scanned barcode string
      .joinLeft(claimedTickets).on(_.id === _.ticketId)
      .result
      .head // Only get the first one
      .flatMap {
      case (ticketData, optClaimed) => {
        def findByProduct(): DBIOAction[Option[Int], NoStream, Effect.Read] = {
          orderedProductTickets.filter(_.ticketId === ticketData.id) // find the orderedProductTicket by ticketId
            .join(productJoin).on(_.orderedProductId === _._1.id) // find the product by its ticket
            .join(orders).on(_._2._1.orderId === _.id) // find the order corresponding to the products
            .map(_._2.id) // only get the order id
            .result
            .headOption
        }

        def findByOrder(): DBIOAction[Option[Int], NoStream, Effect.Read] = {
          orderTickets.filter(_.ticketId === ticketData.id).map(_.ticketId).result.headOption
        }

        val orderIdFinder = Barcodes.parseCode(barcode) match {
          case ProductCode => findByProduct()
          case OrderCode => findByOrder()
          case _ =>
            // We try to find a product then an order
            // Some barcodes (like the one imported from resellers) might not follow the format and therefore not be
            // recognized
            findByProduct().flatMap(result => {
              if (result.isDefined) DBIO.successful(result)
              else findByOrder()
            })
        }

        orderIdFinder.map(optId => optId.map(id => (ticketData, id)))
      }
    }

    db.run(req).recover {
      case e: NoSuchElementException => None
      case e: UnknownError => None
      case e: UnsupportedOperationException => None // no result (empty.head)
    }
  }

  private def findOrder(barcode: String, barcodeId: Int): DBIOAction[Option[(GeneratedBarCode, data.Client, Int)], NoStream, Effect.Read] = {
    val join =
      orderTickets filter (_.ticketId === barcodeId) join // find the orderTicket by ticketId
        orders on ((left, right) => left.orderId === right.id && !right.removed) map (_._2) join // find the order by its ticket
        clients on (_.clientId === _.id) join // find the client who made the order
        productJoin on (_._1.id === _._1.orderId) map { // find products in the order
        case ((order, client), (_, product, event)) => (order.id, product, event, client)
      }

    join.result.map(seq => {
      val products = seq.map { case (_, product, _, _) => product }.filterNot(p => p.isTicket).groupMapReduce(p => p)(p => 1)(_ + _)

      seq.headOption.map {
        case (orderId, _, event, client) => (OrderBarCode(orderId, products, barcode, event), client, barcodeId)
      }
    })
  }

  private def findProduct(barcode: String, barcodeId: Int): DBIOAction[Option[(GeneratedBarCode, data.Client, Int)], NoStream, Effect.Read] = {
    val join =
      orderedProductTickets.filter(_.ticketId === barcodeId) // find the orderedProductTicket by ticketId
        .join(productJoin).on(_.orderedProductId === _._1.id) // find the product by its ticket
        .join(orders.filterNot(_.removed)).on(_._2._1.orderId === _.id) // find the order corresponding to the products
        .join(clients).on(_._2.clientId === _.id) map { // find the client who made the order
        case (((_, (_, p, e)), _), client) => (p, e, client)
      } // keep only product, event and client

    join.result.map(_ map { case (p, e, client) => (TicketBarCode(p, barcode, e), client, barcodeId) } headOption)
  }

  /**
    * Mark an order as paid
    *
    * @param order the order to mark as paid
    * @return a future, containing 1 or an error
    */
  def markAsPaid(order: Int, date: Timestamp = new Timestamp(System.currentTimeMillis())): Future[Int] = {
    db.run(
      orders
        .filter(_.id === order)
        .map(_.paymentConfirmed)
        .update(Some(date))
        .filter(r => r == 1) // ensure we updated only one item
    )
  }

  /**
    * Create an order, then insert it in the database, and return its id as well as the total order price
    *
    * @param map    the sanitized & checked map of the order
    * @param user   the user making the request
    * @param source the source of the order
    * @return a future holding all the [[CheckedOutItem]], as well as the inserted order ID and the total price
    */
  def postOrder(user: AuthenticatedUser, map: Map[Product, Seq[CheckedOutItem]], source: Source): Future[(Iterable[CheckedOutItem], Int, Double)] = {
    def sumPrice(list: Iterable[CheckedOutItem]) = list.map(p => p.itemPrice.get * p.itemAmount).sum

    if (source == data.Reseller)
      throw new IllegalArgumentException("Order source cannot be Reseller")

    val ticketsPrice = sumPrice(map.filter { case (product, _) => product.isTicket }.values.flatten)
    val totalPrice = sumPrice(map.values.flatten)

    val order =
      if (source == Gift) Order(Option.empty, user.id, 0D, 0D, source = Gift)
      else Order(Option.empty, user.id, ticketsPrice, totalPrice, source = source)

    createOrder(order).map((map.values.flatten, _, totalPrice))
  }

  private def confirmOrderQuery(order: Int) = orders
    .filterNot(_.removed)
    .filter(_.id === order)
    .map(_.paymentConfirmed)
    .filter(_.isEmpty)
    .update(Some(new Timestamp(System.currentTimeMillis())))
    .flatMap(r => if (r >= 1) DBIO.successful(()) else DBIO.failed(new IllegalStateException()))

  def setOrderAccepted(order: Int) = {
    db.run(confirmOrderQuery(order))
  }

  /**
    * Accept an order by its id, generating barcodes and returning them.
    *
    * @param order the order to accept
    * @return a future holding the barcodes as well as the client to which they should be sent
    */
  def acceptOrder(order: Int): Future[(Seq[GeneratedBarCode], data.Client, data.Order)] = {
    // Get the content of the order
    val orderedProducts = productJoin
      .filter { case (orderedProduct, _, _) => orderedProduct.orderId === order }
      .result

    val (orderedTickets, orderedGoodies) = {
      val part = orderedProducts.map(_.partition(_._2.isTicket))
      (part.map(_._1), part.map(_._2))
    }

    // This query insert barcodes for the tickets and return them
    // Due to DB constraints, a given ordered_product cannot have more than one ticket
    // Thanks to atomic execution, if a given order has already been IPN-ed, its tickets won't be regenerated
    val insertTicketBarcodes: DBIOAction[Seq[GeneratedBarCode], _, _] =
    orderedTickets
      .flatMap { result =>
        DBIO.sequence(
          result
            .map(product => (product, Ticket(Option.empty, barcodeGen(ProductCode))))
            .map {
              case ((orderedProduct, product, event), ticket) =>
                // Add a ticket in the database and get its id back
                ((tickets returning tickets.map(_.id)) += ticket)
                  // Create a pair (orderedProduct, ticket)
                  .map(ticketId => (orderedProduct.id.get, ticketId))
                  // Insert that pair
                  .flatMap(pair => orderedProductTickets += pair)
                  // Map the result to return a TicketBarCode object
                  .map(_ => TicketBarCode(product, ticket.barCode, event))
            }
        )
      }.transactionally // Do all this atomically to prevent the creation of thousands of useless tickets

    // This query inserts a single ticket for the order and return its barcode
    val insertOrderBarcode = orderedGoodies
      .flatMap(goodies => {
        if (goodies.nonEmpty) {
          val event = goodies.head._3
          val products = goodies.map { case (_, product, _) => product }.groupMapReduce(p => p)(p => 1)(_ + _)
          // If we have items:
          // Create a ticket
          val ticket = Ticket(Option.empty, barcodeGen(OrderCode))
          ((tickets returning tickets.map(_.id)) += ticket)
            // Link it to the order
            .flatMap(ticketId => orderTickets += (order, ticketId))
            .map(_ => Some(OrderBarCode(order, products, ticket.barCode, event)))
        } else {
          DBIO.successful(None)
        }
      }).transactionally

    val insertBarCodes = insertTicketBarcodes.flatMap(tickets => {
      insertOrderBarcode.map {
        case Some(orderBarCode) => tickets :+ orderBarCode
        case None => tickets
      }
    })


    val updateRemainingStock =
      orderedProducts
        .map(_.filter(_._2.maxItems > 0)) // get products that have limited quantities
        .map(res => res.groupBy(_._2).mapValues(_.size)) // keep only the number of products ordered
        .flatMap(res => DBIO.sequence(
        res.map {
          case (p, num) =>
            val newQuantity = Math.max(p.maxItems - num, 0)
            products.filter(_.id === p.id).map(_.maxItems).update(newQuantity) // decrease the maxItems value
        }
      ))

    val insertAndReturnCodes = insertBarCodes // get the order code
      .flatMap(codes => orderJoin
      .filter(_._1.id === order).result.head
      .map {
        case (client, order) => (codes, order, client) // get the client
      })


    // This query updates the confirm time of the order
    val updateConfirmTime = confirmOrderQuery(order)

    db.run((updateConfirmTime >> updateRemainingStock >> insertAndReturnCodes).transactionally).recover {
      case _: SQLIntegrityConstraintViolationException | _: IllegalStateException =>
        println("Duplicate IPN request for " + order + ", returning empty result")
        (Seq(), null, null)
    }
  }
}

object OrdersModel {

  /**
    * A trait representing a barcode
    */
  sealed trait GeneratedBarCode {
    val barcode: String
    val event: data.Event
  }

  /**
    * This class represents a barcode bound to a ticket (i.e. a product that allow you to enter the event)
    *
    * @param product the product representing the ticket (here: the product) (useful to find the template for the PDF generation)
    * @param barcode the actual barcode for the ticket
    */
  case class TicketBarCode(product: data.Product, override val barcode: String, override val event: data.Event) extends GeneratedBarCode

  /**
    * This class represents a barcode bound to an order<br>
    * Such barcodes are generated only for orders that contain at least one non-ticket item (i.e. goodies)
    *
    * @param order   the order this ticket/barcode is bound to
    * @param barcode the actual barcode for the ticket
    */
  case class OrderBarCode(order: Int, products: Map[data.Product, Int], override val barcode: String, override val event: data.Event) extends GeneratedBarCode

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
case class JsonOrder(id: Int, price: Double, paymentConfirmed: Boolean, createdAt: Long, source: String, removed: Boolean)

case object JsonOrder {
  /**
    * Convert a database order to a [[JsonOrder]]. The given order MUST have already been inserted.
    *
    * @param order the order to convert
    * @return a [[JsonOrder]]
    */
  def apply(order: data.Order): JsonOrder = order match {
    case Order(Some(id), _, _, price, paymentConfirmed, Some(enterDate), source, removed) =>
      JsonOrder(id, price, paymentConfirmed.isDefined, enterDate.getTime, source.toString, removed)
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
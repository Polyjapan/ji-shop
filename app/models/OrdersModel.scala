package models

import java.security.SecureRandom
import java.sql.{SQLIntegrityConstraintViolationException, Timestamp}

import data.{Order, OrderedProduct, Ticket}
import javax.inject.Inject
import models.OrdersModel.{GeneratedBarCode, OrderBarCode, TicketBarCode}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

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
  private val ticketTickets = tickets join orderedProductTickets on (_.id === _.ticketId)
  private val orderJoin = orders join clients on (_.clientId === _.id)

  private def barcodeGen: String = {
    val bytes = new Array[Byte](8)
    new SecureRandom().nextBytes(bytes)

    BigInt(bytes).toString.takeRight(15)
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
        DBIO.sequence(a.map(product => (product, Ticket(Option.empty, barcodeGen))).map(pair =>
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
      val ticket = Ticket(Option.empty, barcodeGen)
      ((tickets returning tickets.map(_.id)) += ticket)
        // Link it to the order
        .flatMap(ticketId => orderTickets += (order, ticketId))
        .flatMap(_ => DBIO.successful(OrderBarCode(order, products, ticket.barCode, event)))
    })).transactionally

    val q3 = orderJoin.filter(_._1.id === order).take(1).result

    val result = qq1 flatMap
      (seq => qq2.flatMap(code => DBIO.successful(seq :+ code))) flatMap
      (seq => q3.flatMap(cli => DBIO.successful((seq, cli.head._2))))


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
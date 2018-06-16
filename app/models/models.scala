import java.sql.Timestamp

import data._
import slick.jdbc.MySQLProfile.api._

/**
  * This package object contains all private Slick [[Table]] objects that are used in multiple models and their
  * associated [[TableQuery]].
  */
package object models {
  private[models] class Clients(tag: Tag) extends Table[Client](tag, "clients") {
    def id = column[Int]("client_id", O.PrimaryKey, O.AutoInc)
    def firstname = column[String]("client_firstname", O.SqlType("VARCHAR(100)"))
    def lastname = column[String]("client_lastname", O.SqlType("VARCHAR(100)"))
    def email = column[String]("client_email", O.SqlType("VARCHAR(180)"), O.Unique)
    def emailConfirmKey = column[Option[String]]("client_email_confirm_key", O.SqlType("VARCHAR(100)"))
    def password = column[String]("client_password", O.SqlType("VARCHAR(250)"))
    def passwordAlgo = column[String]("client_password_algo", O.SqlType("VARCHAR(15)"))
    def passwordReset = column[Option[String]]("client_password_reset", O.SqlType("VARCHAR(250)"))
    def passwordResetEnd = column[Option[Timestamp]]("client_password_reset_end")

    def * =
      (id.?, firstname, lastname, email, emailConfirmKey, password, passwordAlgo, passwordReset, passwordResetEnd).shaped <> (Client.tupled, Client.unapply)
  }

  private[models] val clients = TableQuery[Clients]

  private[models] class Events(tag: Tag) extends Table[Event](tag, "events") {
    def id = column[Int]("event_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("event_name", O.SqlType("VARCHAR(250)"))
    def location = column[String]("event_location", O.SqlType("VARCHAR(250)"))
    def visible = column[Boolean]("event_visible")

    def * =
      (id.?, name, location, visible).shaped <> (Event.tupled, Event.unapply)
  }

  private[models] val events = TableQuery[Events]

  implicit val sourceMap = MappedColumnType.base[Source, String](Source.unapply, Source.apply)

  private[models] class Orders(tag: Tag) extends Table[Order](tag, "orders") {
    def id = column[Int]("order_id", O.PrimaryKey, O.AutoInc)
    def clientId = column[Int]("client_id")
    def ticketsPrice = column[Double]("order_tickets_price")
    def totalPrice = column[Double]("order_total_price")
    def paymentConfirmed = column[Option[Timestamp]]("order_payment_confirmed")
    def enterDate = column[Timestamp]("order_enter_date", O.SqlType("timestamp DEFAULT now()"))
    def source = column[Source]("order_source", O.SqlType("SET('WEB', 'ONSITE', 'RESELLER', 'GIFT') DEFAULT 'WEB'"))

    def client = foreignKey("order_client_fk", clientId, clients)(_.id)

    def * =
      (id.?, clientId, ticketsPrice, totalPrice, paymentConfirmed, enterDate.?, source).shaped <> (Order.tupled, Order.unapply)
  }
  private[models] val orders = TableQuery[Orders]


  private[models] class Tickets(tag: Tag) extends Table[Ticket](tag, "tickets") {
    def id = column[Int]("ticket_id", O.PrimaryKey, O.AutoInc)
    def createdAt = column[Timestamp]("ticket_created_at", O.SqlType("timestamp DEFAULT now()"))
    def barCode = column[String]("ticket_bar_code", O.SqlType("VARCHAR(50)"), O.Unique)


    def * =
      (id.?, barCode, createdAt.?).shaped <> (Ticket.tupled, Ticket.unapply)
  }
  private[models] val tickets = TableQuery[Tickets]


  private[models] class OrderedProductTickets(tag: Tag) extends Table[(Int, Int)](tag, "ordered_products_tickets") {
    def orderedProductId = column[Int]("ordered_product_id", O.PrimaryKey, O.Unique)
    def ticketId = column[Int]("ticket_id", O.PrimaryKey, O.Unique)

    def product = foreignKey("ordered_products_tickets_product_fk", orderedProductId, orderedProducts)(_.id)
    def ticket = foreignKey("ordered_products_tickets_ticket_fk", ticketId, tickets)(_.id)

    def * = (orderedProductId, ticketId).shaped
  }
  private[models] val orderedProductTickets = TableQuery[OrderedProductTickets]

  private[models] class OrdersTickets(tag: Tag) extends Table[(Int, Int)](tag, "orders_tickets") {
    def orderId = column[Int]("order_id", O.PrimaryKey, O.Unique)
    def ticketId = column[Int]("ticket_id", O.PrimaryKey, O.Unique)

    def order = foreignKey("orders_tickets_order_fk", orderId, orders)(_.id)
    def ticket = foreignKey("orders_tickets_ticket_fk", ticketId, tickets)(_.id)

    def * = (orderId, ticketId).shaped
  }
  private[models] val orderTickets = TableQuery[OrdersTickets]



  private[models] class ClaimedTickets(tag: Tag) extends Table[ClaimedTicket](tag, "claimed_tickets") {
    def ticketId = column[Int]("ticket_id", O.PrimaryKey)
    def claimedAt = column[Timestamp]("ticket_claimed_at", O.SqlType("timestamp DEFAULT now()"))
    def claimedBy = column[Int]("ticket_claimed_by_admin")

    def client = foreignKey("claimed_tickets_client_fk", claimedBy, clients)(_.id)
    def ticket = foreignKey("claimed_tickets_ticket_fk", ticketId, tickets)(_.id)


    def * =
      (ticketId, claimedAt, claimedBy).shaped <> (ClaimedTicket.tupled, ClaimedTicket.unapply)
  }
  private[models] val claimedTickets = TableQuery[ClaimedTickets]


  private[models] class Products(tag: Tag) extends Table[Product](tag, "products") {
    def id = column[Int]("product_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("product_name", O.SqlType("VARCHAR(250)"))
    def price = column[Double]("product_price")
    def description = column[String]("product_description")
    def longDescription  = column[String]("product_long_description")
    def maxItems = column[Int]("product_max_items")
    def eventId = column[Int]("event_id")
    def isTicket = column[Boolean]("is_ticket")
    def freePrice = column[Boolean]("product_free_price")
    def isVisible = column[Boolean]("is_visible")

    def category = foreignKey("product_event_fk", eventId, events)(_.id)

    def * =
      (id.?, name, price, description, longDescription, maxItems, eventId, isTicket, freePrice, isVisible).shaped <> (Product.tupled, Product.unapply)
  }

  private[models] val products = TableQuery[Products]

  private[models] class OrderedProducts(tag: Tag) extends Table[OrderedProduct](tag, "ordered_products") {
    def id = column[Int]("ordered_product_id", O.PrimaryKey, O.AutoInc)
    def productId = column[Int]("product_id")
    def orderId = column[Int]("order_id")
    def paidPrice = column[Double]("ordered_product_paid_price")

    def product = foreignKey("ordered_product_product_fk", productId, products)(_.id)
    def order = foreignKey("ordered_product_order_fk", orderId, orders)(_.id)

    def * =
      (id.?, productId, orderId, paidPrice).shaped <> (OrderedProduct.tupled, OrderedProduct.unapply)
  }

  private[models] val orderedProducts = TableQuery[OrderedProducts]
}

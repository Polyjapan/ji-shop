import java.sql.Timestamp

import akka.stream.stage.TimerGraphStageLogic
import data._
import models.ScanningConfigurations
import slick.jdbc.MySQLProfile.api._

/**
  * This package object contains all private Slick [[Table]] objects that are used in multiple models and their
  * associated [[TableQuery]].
  */
package object models {
  private[models] class Clients(tag: Tag) extends Table[Client](tag, "clients") {
    def id = column[Int]("client_id", O.PrimaryKey, O.AutoInc)
    def casId = column[Int]("client_cas_user_id", O.Unique)
    def firstname = column[String]("client_firstname", O.SqlType("VARCHAR(100)"))
    def lastname = column[String]("client_lastname", O.SqlType("VARCHAR(100)"))
    def email = column[String]("client_email", O.SqlType("VARCHAR(180)"), O.Unique)
    def acceptNews = column[Boolean]("client_accept_newsletter", O.Default(false))

    def * =
      (id.?, casId, lastname, firstname, email, acceptNews).shaped <> (Client.tupled, Client.unapply)
  }

  private[models] val clients = TableQuery[Clients]

  private[models] class Events(tag: Tag) extends Table[Event](tag, "events") {
    def id = column[Int]("event_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("event_name", O.SqlType("VARCHAR(250)"))
    def location = column[String]("event_location", O.SqlType("VARCHAR(250)"))
    def image = column[Option[String]]("event_tickets_image", O.SqlType("VARCHAR(250) NULL"))
    def visible = column[Boolean]("event_visible")
    def archived = column[Boolean]("event_archived", O.Default(false))

    def * =
      (id.?, name, location, image, visible, archived).shaped <> (Event.tupled, Event.unapply)
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
    def source = column[Source]("order_source", O.SqlType("SET('WEB', 'ONSITE', 'RESELLER', 'GIFT', 'PHYSICAL') DEFAULT 'WEB'"))
    def removed = column[Boolean]("order_removed")

    def client = foreignKey("order_client_fk", clientId, clients)(_.id)

    def * =
      (id.?, clientId, ticketsPrice, totalPrice, paymentConfirmed, enterDate.?, source, removed).shaped <> (Order.tupled, Order.unapply)
  }
  private[models] val orders = TableQuery[Orders]


  private[models] class Tickets(tag: Tag) extends Table[Ticket](tag, "tickets") {
    def id = column[Int]("ticket_id", O.PrimaryKey, O.AutoInc)
    def createdAt = column[Timestamp]("ticket_created_at", O.SqlType("timestamp DEFAULT now()"))
    def barCode = column[String]("ticket_bar_code", O.SqlType("VARCHAR(50)"), O.Unique)
    def removed = column[Boolean]("ticket_removed")


    def * =
      (id.?, barCode, createdAt.?, removed).shaped <> (Ticket.tupled, Ticket.unapply)
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
    def image = column[Option[String]]("product_image", O.SqlType("VARCHAR(255)"))
    def isExclusive = column[Boolean]("is_web_exclusive")
    def realPrice = column[Int]("product_real_price")

    def category = foreignKey("product_event_fk", eventId, events)(_.id)

    def * =
      (id.?, name, price, description, longDescription, maxItems, eventId, isTicket, freePrice, isVisible, image,
        isExclusive, realPrice).shaped <> (Product.tupled, Product.unapply)
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


  private[models] class ScanningConfigurations(tag: Tag) extends Table[ScanningConfiguration](tag, "scanning_configurations") {
    def id = column[Int]("scanning_configuration_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("event_id")
    def name = column[String]("scanning_configuration_name", O.SqlType("VARCHAR(250)"))
    def acceptOrderTickets = column[Boolean]("accept_order_tickets")

    def event = foreignKey("scanning_configurations_events_event_id_fk", eventId, events)(_.id)

    def * =
      (id.?, eventId, name, acceptOrderTickets).shaped <> (ScanningConfiguration.tupled, ScanningConfiguration.unapply)
  }
  private[models] val scanningConfigurations = TableQuery[ScanningConfigurations]

  private[models] class ScanningItems(tag: Tag) extends Table[ScanningItem](tag, "scanning_items") {
    def scanningConfigurationId = column[Int]("scanning_configuration_id")
    def acceptedItemId = column[Int]("product_id")

    def configuration = foreignKey("scanning_items_config_fk", scanningConfigurationId, scanningConfigurations)(_.id)
    def item = foreignKey("scanning_items_item_fk", acceptedItemId, products)(_.id)
    def primary = primaryKey("scanning_items_pk", (scanningConfigurationId, acceptedItemId))

    def * =
      (scanningConfigurationId, acceptedItemId).shaped <> (ScanningItem.tupled, ScanningItem.unapply)
  }

  private[models] val scanningItems = TableQuery[ScanningItems]



  private[models] class PosConfigurations(tag: Tag) extends Table[PosConfiguration](tag, "pos_configurations") {
    def id = column[Int]("pos_configuration_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("event_id")
    def name = column[String]("pos_configuration_name", O.SqlType("VARCHAR(250)"))
    def acceptCards = column[Boolean]("pos_configuration_accept_cards")

    def event = foreignKey("pos_configurations_events_event_id_fk", eventId, events)(_.id)

    def * =
      (id.?, eventId, name, acceptCards).shaped <> (PosConfiguration.tupled, PosConfiguration.unapply)
  }

  private[models] val posConfigurations = TableQuery[PosConfigurations]

  private[models] class PosConfigItems(tag: Tag) extends Table[PosConfigItem](tag, "pos_items") {
    def configId = column[Int]("pos_configuration_id")
    def itemId = column[Int]("product_id")
    def row = column[Int]("row")
    def col = column[Int]("col")
    def color = column[String]("color", O.SqlType("VARCHAR(50)"))
    def fontColor = column[String]("font_color", O.SqlType("VARCHAR(50)"))

    def primary = primaryKey("pos_items_pk", (configId, itemId))
    def configuration = foreignKey("pos_items_config_fk", configId, posConfigurations)(_.id)
    def item = foreignKey("pos_items_item_fk", itemId, products)(_.id)

    def * =
      (configId, itemId, row, col, color, fontColor).shaped <> (PosConfigItem.tupled, PosConfigItem.unapply)
  }

  private[models] val posConfigItems = TableQuery[PosConfigItems]

  implicit val methodMap = MappedColumnType.base[PaymentMethod, String](PaymentMethod.unapply, PaymentMethod.apply)

  /*
  cardTransactionMessage: Option[String],
                           cardTransactionCode: Option[String],
                           cardReceiptSend: Option[Boolean],
                           cardFailureCause: Option[String]
   */

  private[models] class PosPaymentLogs(tag: Tag) extends Table[PosPaymentLog](tag, "pos_payment_logs") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def orderId = column[Int]("order_id")
    def paymentMethod = column[PaymentMethod]("pos_payment_method", O.SqlType("SET('CASH', 'CARD')"))
    def logDate = column[Timestamp]("log_date", O.SqlType("TIMESTAMP DEFAULT now()"))
    def accepted = column[Boolean]("accepted")

    def cardTransactionCode = column[Option[String]]("card_transaction_code", O.SqlType("VARCHAR(250) NULL"))
    def cardTransactionFailureCause = column[Option[String]]("card_transaction_failure_cause", O.SqlType("VARCHAR(250) NULL"))
    def cardReceiptSent = column[Option[Boolean]]("card_receipt_sent", O.Default(Some(false)))
    def cardTransactionMessage = column[Option[String]]("card_transaction_message", O.SqlType("VARCHAR(250) NULL"))

    /*
      CONSTRAINT pos_payment_logs_fk FOREIGN KEY (order_id) REFERENCES orders (order_id)
     */
    def ordersFk = foreignKey("pos_payment_logs_fk", orderId, orders)(_.id)

    def * =
      (id.?, orderId, paymentMethod, logDate, accepted, cardTransactionMessage, cardTransactionCode,
        cardReceiptSent, cardTransactionFailureCause)
        .shaped <> (PosPaymentLog.tupled, PosPaymentLog.unapply)
  }


  private[models] val posPaymentLogs = TableQuery[PosPaymentLogs]

  private[models] class OrderLogs(tag: Tag) extends Table[OrderLog](tag, "order_logs") {
    def id = column[Int]("order_log_id", O.PrimaryKey, O.AutoInc)
    def orderId = column[Int]("order_id")
    def logDate = column[Timestamp]("order_log_date", O.SqlType("TIMESTAMP DEFAULT CURRENT_TIMESTAMP"))
    def accepted = column[Boolean]("order_log_accepted")

    def name = column[String]("order_log_name", O.SqlType("VARCHAR(255) NOT NULL"))
    def details = column[Option[String]]("order_log_details", O.SqlType("TEXT NULL"))

    /*
      CONSTRAINT pos_payment_logs_fk FOREIGN KEY (order_id) REFERENCES orders (order_id)
     */
    def ordersFk = foreignKey("order_logs_orders_order_id_fk", orderId, orders)(_.id)

    def * =
      (id.?, orderId, logDate, name, details, accepted)
        .shaped <> (OrderLog.tupled, OrderLog.unapply)
  }

  private[models] val orderLogs = TableQuery[OrderLogs]


  private[models] class RefreshTokens(tag: Tag) extends Table[RefreshToken](tag, "refresh_tokens") {
    def id = column[Int]("refresh_token_id", O.PrimaryKey, O.AutoInc)
    def clientId = column[Int]("client_id")
    def created = column[Timestamp]("refresh_token_created", O.SqlType("TIMESTAMP DEFAULT now()"))
    def disabled = column[Boolean]("refresh_token_disabled")
    def userAgent = column[String]("refresh_token_user_agent")

    def client = foreignKey("refresh_tokens_clients_client_id_fk", clientId, clients)(_.id)

    def * = (id.?, clientId, created.?, disabled, userAgent).shaped <> (RefreshToken.tupled, RefreshToken.unapply)
  }

  private[models] val refreshTokens = TableQuery[RefreshTokens]

  private[models] class RefreshTokenLogs(tag: Tag) extends Table[RefreshTokenLog](tag, "refresh_tokens_logs") {
    def id = column[Int]("refresh_token_id")
    def time = column[Timestamp]("refresh_tokens_log_time", O.SqlType("TIMESTAMP DEFAULT now()"))
    def userAgent = column[String]("refresh_tokens_log_user_agent")
    def ip = column[String]("refresh_tokens_log_ip")

    def token = foreignKey("refresh_tokens_logs_refresh_tokens_refresh_token_id_fk", id, refreshTokens)(_.id)

    def * = (id, time.?, userAgent, ip).shaped <> (RefreshTokenLog.tupled, RefreshTokenLog.unapply)
  }

  private[models] val refreshTokenLogs = TableQuery[RefreshTokenLogs]

  //   case class Image(id: Option[Int], event: Int, category: String, name: String, width: Int, height: Int, size: Int)
  private[models] class Images(tag: Tag) extends Table[Image](tag, "images") {
    def id = column[Int]("image_id", O.AutoInc)
    def category = column[String]("image_category")
    def name = column[String]("image_name")
    def width = column[Int]("image_width")
    def height = column[Int]("image_height")
    def size = column[Int]("image_size_bytes")

    def * = (id.?, category, name, width, height, size).shaped <> (Image.tupled, Image.unapply)
  }

  private[models] val images = TableQuery[Images]
}

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
    def desc = column[Option[String]]("event_description", O.SqlType("TEXT NULL"))
    def visible = column[Boolean]("event_visible")
    def archived = column[Boolean]("event_archived", O.Default(false))

    def * =
      (id.?, name, location, image, desc, visible, archived).shaped <> (Event.tupled, Event.unapply)
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

  private[models] class PosConfigurations(tag: Tag) extends Table[PosConfiguration](tag, "pos_configurations") {
    def id = column[Int]("pos_configuration_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("event_id")
    def name = column[String]("pos_configuration_name", O.SqlType("VARCHAR(250)"))
    def acceptCards = column[Boolean]("pos_configuration_accept_cards")
    def acceptCamipro = column[Boolean]("pos_configuration_accept_camipro")

    def event = foreignKey("pos_configurations_events_event_id_fk", eventId, events)(_.id)

    def * =
      (id.?, eventId, name, acceptCards, acceptCamipro).shaped <> (PosConfiguration.tupled, PosConfiguration.unapply)
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
    def paymentMethod = column[PaymentMethod]("pos_payment_method", O.SqlType("SET('CASH', 'CARD', 'CAMIPRO')"))
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

}

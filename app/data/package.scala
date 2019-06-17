import java.sql.Timestamp

import play.api.libs.json._

/**
  * @author zyuiop
  */
package object data {

  /**
    * Defines a Client
    *
    * @param id        the id of the client in database
    * @param casId     the id of the client in the CAS database
    * @param lastname  the last name of the client
    * @param firstname the first name of the client
    * @param email     the email of the client
    */
  case class Client(id: Option[Int], casId: Int, lastname: String, firstname: String, email: String, acceptNewsletter: Boolean)


  implicit val clientFormat: Format[Client] = Json.format[Client]


  /**
    * Defines an Event, in general it will be a Japan Impact edition, but who knows what could come next?
    *
    * @param id       the id of the event
    * @param name     the name of the event (e.g. "Japan Impact 10")
    * @param location the location where the event takes place (e.g. "EPFL, Lausanne")
    * @param visible  whether or not the event is visible (in general, you only want a single visible event)
    * @param archived whether or not the event is archived
    */
  case class Event(id: Option[Int], name: String, location: String, visible: Boolean, archived: Boolean)

  sealed trait Source

  /**
    * An order made directly from the website. The clientId is then the client who made the order.
    */
  case object Web extends Source

  /**
    * An order made and paid on site, at a checkout. The clientId is the id of the staff that registered the order
    */
  case object OnSite extends Source

  /**
    * An order made and paid on a PolyJapan owned salepoint (i.e. Agepoly shop, advent calendar...). The clientId is the$
    * id of the staff that registered the order
    */
  case object Physical extends Source

  /**
    * An order made on an external site (e.g. fnac.ch) that was imported in the database. The clientId is the id of the
    * admin who imported the database
    */
  case object Reseller extends Source

  /**
    * An order made by an admin to generate free tickets. The clientId is the id of the admin who generated the tickets.
    */
  case object Gift extends Source

  object Source {
    def unapply(arg: Source): String = arg.toString.toUpperCase

    def apply(string: String): Source = asOpt(string).get

    def asOpt(string: String): Option[Source] = string.toUpperCase match {
      case "ONSITE" => Some(OnSite)
      case "RESELLER" => Some(Reseller)
      case "WEB" => Some(Web)
      case "GIFT" => Some(Gift)
      case "PHYSICAL" => Some(Physical)
      case _ => None
    }

    implicit val sourceFormat: Format[Source] = new Format[Source] {
      override def reads(json: JsValue): JsResult[Source] = json match {
        case JsString(str) => JsSuccess(Source(str))
        case _ => JsError("Invalid type")
      }

      override def writes(o: Source): JsValue = JsString(Source.unapply(o))
    }

  }

  /**
    * Describes an order in the shop.
    *
    * @param id               the id of the order
    * @param clientId         the id of the client generating the order
    * @param ticketsPrice     the price paid by the user for tickets only
    * @param totalPrice       the price paid by the user in total
    * @param paymentConfirmed if null, then the payment hasn't been confirmed yet. If not null, the timestamp at which
    *                         the IPN script was called for this order confirming the payment was received
    * @param enterDate        the timestamp at which this order was generated
    * @param source           the source of the order (web, on site, reseller)
    * @param removed          if true, the order has been flagged as removed and should not count in stats
    */
  case class Order(id: Option[Int], clientId: Int, ticketsPrice: Double, totalPrice: Double,
                   paymentConfirmed: Option[Timestamp] = Option.empty, enterDate: Option[Timestamp] = Option.empty, source: Source = Web,
                   removed: Boolean = false)


  /**
    * Describes a product that can be bought
    *
    * @param id                 the id of the product
    * @param name               the name of the product, displayed on the site and the ticket
    * @param price              the price of the product
    * @param description        a short description of the product, that will be displayed alongside the name and on the ticket
    * @param longDescription    a long description of the product, that gives more details about the product
    * @param maxItems           the items in stock, if lower than 0 it means the stock is unlimited
    * @param eventId            the id of the event this product belongs to
    * @param isTicket           if true, the product will be considered as ticket grouped with the other tickets on the
    *                           buying page. Moreover, when buying, the ticket items will be counted to make a ticket price that will be stored
    *                           separately.
    * @param freePrice          if true, the `price` becomes a minimal price and the client can choose to pay more
    * @param isVisible          if false, this product is not visible to the public and cannot be bought via the site
    * @param image              an URL to an image to display with the product
    * @param isWebExclusive     if true, it means the ticket is a web exclusive. It only changes the way
    *                           the product will be displayed and doesn't affect the way it's sold
    * @param estimatedRealPrice an estimation of the price this product would really cost if it was bought separately (only for display purposes)
    */
  case class Product(id: Option[Int], name: String, price: Double, description: String, longDescription: String,
                     maxItems: Int, eventId: Int, isTicket: Boolean, freePrice: Boolean, isVisible: Boolean,
                     image: Option[String], isWebExclusive: Boolean = false, estimatedRealPrice: Int = -1)


  /**
    * Describes a product that has been ordered, i.e. that is part of an order
    *
    * @param id        an id to identify this ordered product
    * @param productId the id of the product that was ordered
    * @param orderId   the id of the order this product is part of
    * @param paidPrice the price the client actually paid for this product
    */
  case class OrderedProduct(id: Option[Int], productId: Int, orderId: Int, paidPrice: Double)

  /**
    * Describes a ticket. A ticket is a standalone thing, that is linked to a product or order via a special table.
    *
    * @param id        an id to identify this ticket
    * @param barCode   a string that will be represented on a barcode on the printed ticket and that can be used to find
    *                  this ticket. It has to be unique
    * @param createdAt the time at which this ticket was created
    * @param removed   if true, the ticked has been flagged as "removed" and should not be validable
    */
  case class Ticket(id: Option[Int], barCode: String, createdAt: Option[Timestamp] = Option.empty, removed: Boolean = false)

  /**
    * Describes the event that a ticket was claimed. A claimed ticket was scanned and cannot be scanned anymore
    *
    * @param ticketId  the id of the ticket that was claimed
    * @param claimedAt the time at which the ticket was claimed
    * @param claimedBy the person who claimed the ticket (the one who scanned it)
    */
  case class ClaimedTicket(ticketId: Int, claimedAt: Timestamp, claimedBy: Int)

  /**
    * Describes a scanning configuration, i.e. a group of accepted barcode types
    *
    * @param id                 the unique id of this configuration
    * @param name               a name identifying the configuration
    * @param acceptOrderTickets if true, order barcodes will be accepted by this configuration
    */
  case class ScanningConfiguration(id: Option[Int], eventId: Int, name: String, acceptOrderTickets: Boolean)

  /**
    * Describes an item that can be scanned by a configuration
    *
    * @param scanningConfiguration the configuration scanning this item (id)
    * @param acceptedItem          the item scanned (id)
    */
  case class ScanningItem(scanningConfiguration: Int, acceptedItem: Int)

  /**
    * Describes a PointOfSale configuration
    *
    * @param id   the id of the configuration
    * @param name the name of the configuration
    */
  case class PosConfiguration(id: Option[Int], eventId: Int, name: String, acceptCards: Boolean)

  /**
    * Describes an item in a PointOfSale configuration
    *
    * @param configurationId the id of the pos configuration
    * @param productId       the id of the product
    * @param row             the row in the grid (starting at 0)
    * @param col             the col in the grid (starting at 0)
    * @param color           the bootstrap class for the background color of the square
    * @param fontColor       the bootstrap class for the font color of the square
    */
  case class PosConfigItem(configurationId: Int, productId: Int, row: Int, col: Int, color: String, fontColor: String)


  case class RefreshToken(id: Option[Int], clientId: Int, created: Option[Timestamp] = None, disabled: Boolean = false, userAgent: String)

  case class RefreshTokenLog(tokenId: Int, time: Option[Timestamp], userAgent: String, ip: String)


  sealed trait PaymentMethod

  case object Cash extends PaymentMethod

  case object Card extends PaymentMethod

  object PaymentMethod {
    def unapply(arg: PaymentMethod): String = arg.toString.toUpperCase

    def apply(string: String): PaymentMethod = string.toUpperCase match {
      case "CASH" => Cash
      case "CARD" => Card
    }
  }

  /**
    * Describes a line of log in a PointOfSale payment processing
    *
    * @param id                     the id of the log
    * @param orderId                the id of the related order
    * @param paymentMethod          the method used to pay
    * @param logDate                the date this log was generated
    * @param accepted               true if the order was accepted by this log
    * @param cardTransactionMessage transaction message received from SumUP, if CARD payment
    * @param cardTransactionCode    transaction code received from SumUP, if CARD payment
    * @param cardReceiptSend        true if a receipt was sent by SumUP, if CARD payment
    * @param cardFailureCause       an error message, if the payment was a CARD payment
    */
  case class PosPaymentLog(id: Option[Int],
                           orderId: Int,
                           paymentMethod: PaymentMethod,
                           logDate: Timestamp,
                           accepted: Boolean,
                           cardTransactionMessage: Option[String],
                           cardTransactionCode: Option[String],
                           cardReceiptSend: Option[Boolean],
                           cardFailureCause: Option[String])

  /**
    * Describes a line of log in an order
    *
    * @param id       the id of the line
    * @param orderId  the order this log is related to
    * @param logDate  the timestamp when this log was generated
    * @param name     a short description of the log
    * @param details  the details of the log, if any
    * @param accepted true if this log caused the order to be accepted
    */
  case class OrderLog(id: Option[Int], orderId: Int, logDate: Timestamp, name: String, details: Option[String],
                      accepted: Boolean)

  implicit val eventFormat = Json.format[Event]
  implicit val productFormat = Json.format[Product]
  implicit val scanningConfigurationFormat = Json.format[ScanningConfiguration]
  implicit val posConfigurationFormat = Json.format[PosConfiguration]

  implicit val tsFormat: Format[Timestamp] = new Format[Timestamp] {
    override def reads(json: JsValue): JsResult[Timestamp] = json match {
      case JsNumber(num) => JsSuccess(new Timestamp(num.longValue()))
      case _ => JsError("Invalid type")
    }

    override def writes(o: Timestamp): JsValue = JsNumber(BigDecimal(o.getTime))
  }

  implicit val methodFormat = new Format[PaymentMethod] {
    override def reads(json: JsValue): JsResult[PaymentMethod] = json match {
      case JsString(str) => JsSuccess(PaymentMethod(str))
      case _ => JsError("Invalid type")
    }

    override def writes(o: PaymentMethod): JsValue = JsString(PaymentMethod.unapply(o))
  }

  implicit val orderFormat = Json.format[Order]
  implicit val posLogFormat = Json.format[PosPaymentLog]
  implicit val logFormat = Json.format[OrderLog]
}

import java.sql.Timestamp

import play.api.libs.json._

/**
  * @author zyuiop
  */
package object data {

  /**
    * Defines a Client
    *
    * @param id               the id of the client in database
    * @param lastname         the last name of the client
    * @param firstname        the first name of the client
    * @param email            the email of the client
    * @param emailConfirmKey  an optional confirmation key for the email. If it's null then the email has been validated
    * @param password         the hashed password of the user
    * @param passwordAlgo     the algorithm used to hash the password
    * @param passwordReset    an optional reset key for the password. If it's null then no password change was requested
    * @param passwordResetEnd an optional timestamp marking the date at which the password reset key will no longer be valid
    *                         If absent, the password reset key is considered invalid
    */
  case class Client(id: Option[Int], lastname: String, firstname: String, email: String, emailConfirmKey: Option[String], password: String,
                    passwordAlgo: String, passwordReset: Option[String] = Option.empty, passwordResetEnd: Option[Timestamp] = Option.empty,
                    acceptNewsletter: Boolean)

  /**
    * Defines an Event, in general it will be a Japan Impact edition, but who knows what could come next?
    *
    * @param id       the id of the event
    * @param name     the name of the event (e.g. "Japan Impact 10")
    * @param location the location where the event takes place (e.g. "EPFL, Lausanne")
    * @param visible  whether or not the event is visible (in general, you only want a single visible event)
    */
  case class Event(id: Option[Int], name: String, location: String, visible: Boolean)

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

    def apply(string: String): Source = string.toUpperCase match {
      case "ONSITE" => OnSite
      case "RESELLER" => Reseller
      case "WEB" => Web
      case "GIFT" => Gift
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
    */
  case class Order(id: Option[Int], clientId: Int, ticketsPrice: Double, totalPrice: Double,
                   paymentConfirmed: Option[Timestamp] = Option.empty, enterDate: Option[Timestamp] = Option.empty, source: Source = Web)

  /**
    * Describes a product that can be bought
    *
    * @param id              the id of the product
    * @param name            the name of the product, displayed on the site and the ticket
    * @param price           the price of the product
    * @param description     a short description of the product, that will be displayed alongside the name and on the ticket
    * @param longDescription a long description of the product, that gives more details about the product
    * @param maxItems        the items in stock, if lower than 0 it means the stock is unlimited
    * @param eventId         the id of the event this product belongs to
    * @param isTicket        if true, the product will be considered as ticket grouped with the other tickets on the
    *                        buying page. Moreover, when buying, the ticket items will be counted to make a ticket price that will be stored
    *                        separately.
    * @param freePrice       if true, the `price` becomes a minimal price and the client can choose to pay more
    * @param isVisible       if false, this product is not visible to the public and cannot be bought via the site
    */
  case class Product(id: Option[Int], name: String, price: Double, description: String, longDescription: String,
                     maxItems: Int, eventId: Int, isTicket: Boolean, freePrice: Boolean, isVisible: Boolean)


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
    */
  case class Ticket(id: Option[Int], barCode: String, createdAt: Option[Timestamp] = Option.empty)

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
  case class ScanningConfiguration(id: Option[Int], name: String, acceptOrderTickets: Boolean)

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
  case class PosConfiguration(id: Option[Int], name: String)

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

  case class PosPaymentLog(id: Option[Int],
                           orderId: Int,
                           paymentMethod: PaymentMethod,
                           logDate: Timestamp,
                           accepted: Boolean,
                           cardTransactionMessage: Option[String],
                           cardTransactionCode: Option[String],
                           cardReceiptSend: Option[Boolean],
                           cardFailureCause: Option[String])

  implicit val eventFormat = Json.format[Event]
  implicit val productFormat = Json.format[Product]
  implicit val scanningConfigurationFormat = Json.format[ScanningConfiguration]
  implicit val posConfigurationFormat = Json.format[PosConfiguration]
}

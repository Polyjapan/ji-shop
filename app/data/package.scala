import java.sql.Timestamp

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
                    passwordAlgo: String, passwordReset: Option[String] = Option.empty, passwordResetEnd: Option[Timestamp] = Option.empty)

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
    */
  case class Product(id: Option[Int], name: String, price: Double, description: String, longDescription: String,
                     maxItems: Int, eventId: Int, isTicket: Boolean, freePrice: Boolean)


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
  case class Ticket(id: Option[Int], barCode: String, createdAt: Timestamp)

  /**
    * Describe the event that a ticket was claimed. A claimed ticket was scanned and cannot be scanned anymore
    *
    * @param ticketId  the id of the ticket that was claimed
    * @param claimedAt the time at which the ticket was claimed
    * @param claimedBy the person who claimed the ticket (the one who scanned it)
    */
  case class ClaimedTicket(ticketId: Int, claimedAt: Timestamp, claimedBy: Int)

  /**
    * Represents the base template for a ticket
    *
    * @param id            the id of this template (the id 0 is reserved for the default template)
    * @param baseImage     the path to the base image of this template, relative to some directory (probably uploads/ or
    *                      something like that)
    * @param templateName  the friendly name of this template
    * @param barCodeX      the x position of the top-left corner of the barcode (0 is the leftmost position)
    * @param barCodeY      the x position of the top-left corner of the barcode (0 is the 1st line of the image)
    * @param barCodeWidth  the width of the barcode
    * @param barCodeHeight the height of the barcode, if it's higher than the width the barcode will be vertical
    */
  case class TicketTemplate(id: Option[Int], baseImage: String, templateName: String, barCodeX: Int, barCodeY: Int, barCodeWidth: Int,
                            barCodeHeight: Int)


  /**
    * Represents a text component on a ticket
    *
    * @param id         the id of this component
    * @param templateId the id of the parent template
    * @param x          the x position (0 is the leftmost position)
    * @param y          the y position (0 is the top position)
    * @param font       path to a font (relative to the same directory as in [[TicketTemplate]]
    * @param fontSize   the font size
    * @param content    the text to display. Put `%{table}.{column}%` to insert a value (i.e. `%products.name%`)
    */
  case class TicketTemplateComponent(id: Option[Int], templateId: Int, x: Int, y: Int, font: String, fontSize: Int, content: String)

}

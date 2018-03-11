import java.sql.Timestamp

/**
  * @author zyuiop
  */
package object data {

  /**
    * Defines a Client
    * @param id the id of the client in database
    * @param lastname the last name of the client
    * @param firstname the first name of the client
    * @param email the email of the client
    * @param emailConfirmKey an optional confirmation key for the email. If it's null then the email has been validated
    * @param password the hashed password of the user
    * @param passwordAlgo the algorithm used to hash the password
    * @param passwordReset an optional reset key for the password. If it's null then no password change was requested
    * @param passwordResetEnd an optional timestamp marking the date at which the password reset key will no longer be valid
    *                         If absent, the password reset key is considered invalid
    */
  case class Client(id: Option[Int], lastname: String, firstname: String, email: String, emailConfirmKey: Option[String], password: String,
                    passwordAlgo: String, passwordReset: Option[String] = Option.empty, passwordResetEnd: Option[Timestamp] = Option.empty)

  /**
    * Defines an Event, in general it will be a Japan Impact edition, but who knows what could come next?
    * @param id the id of the event
    * @param name the name of the event (e.g. "Japan Impact 10")
    * @param location the location where the event takes place (e.g. "EPFL, Lausanne")
    * @param visible whether or not the event is visible (in general, you only want a single visible event)
    */
  case class Event(id: Option[Int], name: String, location: String, visible: Boolean)

  /**
    * Describes a product categories. A product category is tied to an event, and it will only be displayed if the event
    * is currently visible.
    * <br>The ticket categories will be grouped in a single table. If there is only one ticket category for an event, the
    * name won't be shown on the site (but will still be printed on the ticket).
    * @param id the id of the category
    * @param eventId the event for which this category exists
    * @param name the name of the category, displayed on the ticket and on the site [except if `isTicket == true` and
    *             this category is the only one with this eventId and `isTicket == true`]
    * @param isTicket if true, the items in this category will be considered as ticket and displayed separately on the
    *                 buying page. Moreover, when buying, the ticket items will be counted to make a ticket price that
    *                 will be stored separately.
    */
  case class Category(id: Option[Int], eventId: Int, name: String, isTicket: Boolean)


  /**
    * Describes an order in the shop.
    * @param id the id of the order
    * @param clientId the id of the client generating the order
    * @param ticketsPrice the price paid by the user for tickets only (see [[Category]])
    * @param totalPrice the price paid by the user in total
    * @param paymentConfirmed if null, then the payment hasn't been confirmed yet. If not null, the timestamp at which
    *                         the IPN script was called for this order confirming the payment was received
    * @param enterDate the timestamp at which this order was generated
    */
  case class Order(id: Option[Int], clientId: Int, ticketsPrice: Double, totalPrice: Double,
                   paymentConfirmed: Option[Timestamp], enterDate: Option[Timestamp])

  /**
    * Describes a product that can be bought
    * @param id the id of the product
    * @param name the name of the product, displayed on the site and the ticket
    * @param price the price of the product
    * @param description a short description of the product, that will be displayed alongside the name and on the ticket
    * @param longDescription a long description of the product, that gives more details about the product
    * @param maxItems the items in stock, if lower than 0 it means the stock is unlimited
    * @param categoryId the id of the category this product belongs to
    * @param freePrice if true, the `price` becomes a minimal price and the client can choose to pay more
    */
  case class Product(id: Option[Int], name: String, price: Double, description: String, longDescription: String,
                     maxItems: Int, categoryId: Int, freePrice: Boolean)

  /**
    * Some products require additional details when they are purchased, this is what this case class describes
    * @param id the id of the product detail
    * @param productId the id of the product this detail applies to
    * @param name the name of this detail
    * @param description the description of this detail
    * @param dType the type of value expected
    */
  case class ProductDetail(id: Option[Int], productId: Int, name: String, description: String, dType: String)

  object DetailType {
    val EMAIL: String = "email"
    val STRING: String = "string"
    val PHOTO: String = "photo"
  }

  /**
    * Describes a product that has been ordered, i.e. that is part of an order
    * @param id an id to identify this ordered product
    * @param productId the id of the product that was ordered
    * @param orderId the id of the order this product is part of
    * @param paidPrice the price the client actually paid for this product
    * @param barCode a barcode that is generated when the product is ordered and that identifies this product uniquely
    */
  case class OrderedProduct(id: Option[Int], productId: Int, orderId: Int, paidPrice: Double, barCode: String)

  /**
    * Describes a filled [[ProductDetail]] for a given [[OrderedProduct]]
    * @param id an id to identify this filled detail
    * @param orderedProductId the [[OrderedProduct]] this detail is related to
    * @param productDetailId the detail this is the value of
    * @param value the value of the given detail
    */
  case class FilledDetail(id: Option[Int], orderedProductId: Int, productDetailId: Int, value: String)


  /**
    * Represents the base template for a ticket
    * @param id the id of this template (the id 0 is reserved for the default template)
    * @param baseImage the path to the base image of this template, relative to some directory (probably uploads/ or
    *                  something like that)
    * @param templateName the friendly name of this template
    * @param barCodeX the x position of the top-left corner of the barcode (0 is the leftmost position)
    * @param barCodeY the x position of the top-left corner of the barcode (0 is the 1st line of the image)
    * @param barCodeWidth the width of the barcode
    * @param barCodeHeight the height of the barcode, if it's higher than the width the barcode will be vertical
    */
  case class TicketTemplate(id: Option[Int], baseImage: String, templateName: String, barCodeX: Int, barCodeY: Int, barCodeWidth: Int,
                            barCodeHeight: Int)


  /**
    * Represents a text component on a ticket
    * @param id the id of this component
    * @param templateId the id of the parent template
    * @param x the x position (0 is the leftmost position)
    * @param y the y position (0 is the top position)
    * @param font path to a font (relative to the same directory as in [[TicketTemplate]]
    * @param fontSize the font size
    * @param content the text to display. Put `%{table}.{column}%` to insert a value (i.e. `%products.name%`)
    */
  case class TicketTemplateComponent(id: Option[Int], templateId: Int, x: Int, y: Int, font: String, fontSize: Int, content: String)

}

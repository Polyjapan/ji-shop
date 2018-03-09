import java.sql.Timestamp

import slick.jdbc.H2Profile.api._

package object models {
  case class Client(id: Option[Int], lastname: String, firstname: String, email: String, emailConfirmed: Boolean, password: String,
                    passwordAlgo: String, passwordReset: Option[String] = Option.empty)

  private class Clients(tag: Tag) extends Table[Client](tag, "clients") {
    def id = column[Int]("client_id", O.PrimaryKey, O.AutoInc)
    def firstname = column[String]("client_firstname")
    def lastname = column[String]("client_lastname")
    def email = column[String]("client_email", O.Unique)
    def emailConfirmed = column[Boolean]("client_email_confirmed")
    def password = column[String]("client_password")
    def passwordAlgo = column[String]("client_password_algo")
    def passwordReset = column[Option[String]]("client_password_reset")

    def * =
      (id.?, firstname, lastname, email, emailConfirmed, password, passwordAlgo, passwordReset).shaped <> (Client.tupled, Client.unapply)
  }

  val clients = TableQuery[Clients]

  /**
    * Defines an Event, in general it will be a Japan Impact edition, but who knows what could come next?
    * @param id the id of the event
    * @param name the name of the event (e.g. "Japan Impact 10")
    * @param location the location where the event takes place (e.g. "EPFL, Lausanne")
    * @param visible whether or not the event is visible (in general, you only want a single visible event)
    */
  case class Event(id: Option[Int], name: String, location: String, visible: Boolean)

  private class Events(tag: Tag) extends Table[Event](tag, "events") {
    def id = column[Int]("event_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("event_name")
    def location = column[String]("event_location")
    def visible = column[Boolean]("event_visible")

    def * =
      (id.?, name, location, visible).shaped <> (Event.tupled, Event.unapply)
  }

  val events = TableQuery[Events]

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

  private class Categories(tag: Tag) extends Table[Category](tag, "categories") {
    def id = column[Int]("category_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("client_id")
    def name = column[String]("category_name")
    def isTicket = column[Boolean]("category_is_ticket")

    def event = foreignKey("category_event_fk", eventId, events)(_.id)

    def * =
      (id.?, eventId, name, isTicket).shaped <> (Category.tupled, Category.unapply)
  }

  val categories = TableQuery[Categories]

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
  case class Order(id: Option[Int], clientId: Int, ticketsPrice: Double, totalPrice: Double, paymentConfirmed: Option[Timestamp], enterDate: Option[Timestamp])

  private class Orders(tag: Tag) extends Table[Order](tag, "orders") {
    def id = column[Int]("order_id", O.PrimaryKey, O.AutoInc)
    def clientId = column[Int]("client_id")
    def ticketsPrice = column[Double]("order_tickets_price")
    def totalPrice = column[Double]("order_total_price")
    def paymentConfirmed = column[Option[Timestamp]]("order_payment_confirmed", O.Default(null))
    def enterDate = column[Timestamp]("order_enter_date", O.SqlType("timestamp DEFAULT now()"))

    def client = foreignKey("order_client_fk", clientId, clients)(_.id)

    def * =
      (id.?, clientId, ticketsPrice, totalPrice, paymentConfirmed, enterDate.?).shaped <> (Order.tupled, Order.unapply)
  }

  val orders = TableQuery[Orders]

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
  case class Product(id: Option[Int], name: String, price: Double, description: String, longDescription: String, maxItems: Int, categoryId: Int, freePrice: Boolean)

  private class Products(tag: Tag) extends Table[Product](tag, "products") {
    def id = column[Int]("product_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("product_name")
    def price = column[Double]("product_price")
    def description = column[String]("product_description", O.SqlType("TEXT"))
    def longDescription  = column[String]("product_long_description", O.SqlType("TEXT"))
    def maxItems = column[Int]("order_enter_date")
    def categoryId = column[Int]("order_enter_date")
    def freePrice = column[Boolean]("order_enter_date")

    def category = foreignKey("product_category_fk", categoryId, categories)(_.id)

    def * =
      (id.?, name, price, description, longDescription, maxItems, categoryId, freePrice).shaped <> (Product.tupled, Product.unapply)
  }

  val products = TableQuery[Products]

  /**
    * Some products require additional details when they are purchased, this is what this case class describes
    * @param id the id of the product detail
    * @param productId the id of the product this detail applies to
    * @param name the name of this detail
    * @param description the description of this detail
    * @param dType the type of value expected
    */
  case class ProductDetail(id: Option[Int], productId: Int, name: String, description: String, dType: DetailType)

  type DetailType = String
  val EMAIL: DetailType = "email"
  val STRING: DetailType = "string"
  val PHOTO: DetailType = "photo"

  private class ProductDetails(tag: Tag) extends Table[ProductDetail](tag, "product_details") {
    def id = column[Int]("product_detail_id", O.PrimaryKey, O.AutoInc)
    def productId = column[Int]("product_id")
    def name = column[String]("product_detail_name")
    def description = column[String]("product_detail_description", O.SqlType("TEXT"))
    def dType  = column[DetailType]("product_detail_type", O.SqlType("SET('email', 'string', 'photo')"))

    def * =
      (id.?, productId, name, description, dType).shaped <> (ProductDetail.tupled, ProductDetail.unapply)
  }

  val productDetails = TableQuery[ProductDetails]

  /**
    * Describes a product that has been ordered, i.e. that is part of an order
    * @param id an id to identify this ordered product
    * @param productId the id of the product that was ordered
    * @param orderId the id of the order this product is part of
    * @param paidPrice the price the client actually paid for this product
    * @param barCode a barcode that is generated when the product is ordered and that identifies this product uniquely
    */
  case class OrderedProduct(id: Option[Int], productId: Int, orderId: Int, paidPrice: Double, barCode: String)

  private class OrderedProducts(tag: Tag) extends Table[OrderedProduct](tag, "ordered_products") {
    def id = column[Int]("ordered_product_id", O.PrimaryKey, O.AutoInc)
    def productId = column[Int]("product_id")
    def orderId = column[Int]("order_id")
    def paidPrice = column[Double]("ordered_product_paid_price")
    def barCode = column[String]("ordered_product_bar_code", O.Unique)

    def product = foreignKey("ordered_product_product_fk", productId, products)(_.id)
    def order = foreignKey("ordered_product_order_fk", orderId, orders)(_.id)

    def * =
      (id.?, productId, orderId, paidPrice, barCode).shaped <> (OrderedProduct.tupled, OrderedProduct.unapply)
  }

  val orderedProducts = TableQuery[OrderedProducts]

  /**
    * Describes a filled [[ProductDetail]] for a given [[OrderedProduct]]
    * @param id an id to identify this filled detail
    * @param orderedProductId the [[OrderedProduct]] this detail is related to
    * @param productDetailId the detail this is the value of
    * @param value the value of the given detail
    */
  case class FilledDetail(id: Option[Int], orderedProductId: Int, productDetailId: Int, value: String)

  private class FilledDetails(tag: Tag) extends Table[FilledDetail](tag, "filled_details") {
    def id = column[Int]("filled_detail_id", O.PrimaryKey, O.AutoInc)
    def orderedProductId = column[Int]("ordered_product_id")
    def productDetailId = column[Int]("product_detail_id")
    def value = column[String]("filled_detail_value")

    def orderedProduct = foreignKey("filled_detail_ordered_product_fk", orderedProductId, orderedProducts)(_.id)
    def productDetail = foreignKey("filled_detail_product_detail_fk", productDetailId, productDetails)(_.id)

    def * =
      (id.?, orderedProductId, productDetailId, value).shaped <> (FilledDetail.tupled, FilledDetail.unapply)
  }

  val filledDetails = TableQuery[FilledDetails]

}

import java.sql.Timestamp

import slick.jdbc.MySQLProfile.api._

package object models {

  case class Client(id: Option[Int], lastname: String, firstname: String, email: String, emailConfirmed: Boolean, password: String,
                    passwordAlgo: String, passwordReset: Option[String] = Option.empty)

  private[models] class Clients(tag: Tag) extends Table[Client](tag, "clients") {
    def id = column[Int]("client_id", O.PrimaryKey, O.AutoInc)
    def firstname = column[String]("client_firstname", O.SqlType("VARCHAR(100)"))
    def lastname = column[String]("client_lastname", O.SqlType("VARCHAR(100)"))
    def email = column[String]("client_email", O.SqlType("VARCHAR(180)"), O.Unique)
    def emailConfirmed = column[Boolean]("client_email_confirmed")
    def password = column[String]("client_password", O.SqlType("VARCHAR(250)"))
    def passwordAlgo = column[String]("client_password_algo", O.SqlType("VARCHAR(15)"))
    def passwordReset = column[Option[String]]("client_password_reset", O.SqlType("VARCHAR(250)"))

    def * =
      (id.?, firstname, lastname, email, emailConfirmed, password, passwordAlgo, passwordReset).shaped <> (Client.tupled, Client.unapply)
  }

  private[models] val clients = TableQuery[Clients]
  var schemas = clients.schema


  /**
    * Defines an Event, in general it will be a Japan Impact edition, but who knows what could come next?
    * @param id the id of the event
    * @param name the name of the event (e.g. "Japan Impact 10")
    * @param location the location where the event takes place (e.g. "EPFL, Lausanne")
    * @param visible whether or not the event is visible (in general, you only want a single visible event)
    */
  case class Event(id: Option[Int], name: String, location: String, visible: Boolean)

  private[models] class Events(tag: Tag) extends Table[Event](tag, "events") {
    def id = column[Int]("event_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("event_name", O.SqlType("VARCHAR(250)"))
    def location = column[String]("event_location", O.SqlType("VARCHAR(250)"))
    def visible = column[Boolean]("event_visible")

    def * =
      (id.?, name, location, visible).shaped <> (Event.tupled, Event.unapply)
  }

  private[models] val events = TableQuery[Events]
  schemas ++= events.schema

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

  private[models] class Categories(tag: Tag) extends Table[Category](tag, "categories") {
    def id = column[Int]("category_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("client_id")
    def name = column[String]("category_name", O.SqlType("VARCHAR(250)"))
    def isTicket = column[Boolean]("category_is_ticket")

    def event = foreignKey("category_event_fk", eventId, events)(_.id)

    def * =
      (id.?, eventId, name, isTicket).shaped <> (Category.tupled, Category.unapply)
  }

  private[models] val categories = TableQuery[Categories]
  schemas ++= categories.schema

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

  private[models] class Orders(tag: Tag) extends Table[Order](tag, "orders") {
    def id = column[Int]("order_id", O.PrimaryKey, O.AutoInc)
    def clientId = column[Int]("client_id")
    def ticketsPrice = column[Double]("order_tickets_price")
    def totalPrice = column[Double]("order_total_price")
    def paymentConfirmed = column[Option[Timestamp]]("order_payment_confirmed")
    def enterDate = column[Timestamp]("order_enter_date", O.SqlType("timestamp DEFAULT now()"))

    def client = foreignKey("order_client_fk", clientId, clients)(_.id)

    def * =
      (id.?, clientId, ticketsPrice, totalPrice, paymentConfirmed, enterDate.?).shaped <> (Order.tupled, Order.unapply)
  }

  private[models] val orders = TableQuery[Orders]
  schemas ++= orders.schema

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

  private[models] class Products(tag: Tag) extends Table[Product](tag, "products") {
    def id = column[Int]("product_id", O.PrimaryKey, O.AutoInc)
    def name = column[String]("product_name", O.SqlType("VARCHAR(250)"))
    def price = column[Double]("product_price")
    def description = column[String]("product_description")
    def longDescription  = column[String]("product_long_description")
    def maxItems = column[Int]("order_enter_date")
    def categoryId = column[Int]("order_enter_date")
    def freePrice = column[Boolean]("order_enter_date")

    def category = foreignKey("product_category_fk", categoryId, categories)(_.id)

    def * =
      (id.?, name, price, description, longDescription, maxItems, categoryId, freePrice).shaped <> (Product.tupled, Product.unapply)
  }

  private[models] val products = TableQuery[Products]
  schemas ++= products.schema

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

  private[models] class ProductDetails(tag: Tag) extends Table[ProductDetail](tag, "product_details") {
    def id = column[Int]("product_detail_id", O.PrimaryKey, O.AutoInc)
    def productId = column[Int]("product_id")
    def name = column[String]("product_detail_name", O.SqlType("VARCHAR(250)"))
    def description = column[String]("product_detail_description")
    def dType  = column[String]("product_detail_type", O.SqlType("SET('email', 'string', 'photo')"))

    def * =
      (id.?, productId, name, description, dType).shaped <> (ProductDetail.tupled, ProductDetail.unapply)
  }

  private[models] val productDetails = TableQuery[ProductDetails]
  schemas ++= productDetails.schema

  /**
    * Describes a product that has been ordered, i.e. that is part of an order
    * @param id an id to identify this ordered product
    * @param productId the id of the product that was ordered
    * @param orderId the id of the order this product is part of
    * @param paidPrice the price the client actually paid for this product
    * @param barCode a barcode that is generated when the product is ordered and that identifies this product uniquely
    */
  case class OrderedProduct(id: Option[Int], productId: Int, orderId: Int, paidPrice: Double, barCode: String)

  private[models] class OrderedProducts(tag: Tag) extends Table[OrderedProduct](tag, "ordered_products") {
    def id = column[Int]("ordered_product_id", O.PrimaryKey, O.AutoInc)
    def productId = column[Int]("product_id")
    def orderId = column[Int]("order_id")
    def paidPrice = column[Double]("ordered_product_paid_price")
    def barCode = column[String]("ordered_product_bar_code", O.SqlType("VARCHAR(50)"), O.Unique)

    def product = foreignKey("ordered_product_product_fk", productId, products)(_.id)
    def order = foreignKey("ordered_product_order_fk", orderId, orders)(_.id)

    def * =
      (id.?, productId, orderId, paidPrice, barCode).shaped <> (OrderedProduct.tupled, OrderedProduct.unapply)
  }

  private[models] val orderedProducts = TableQuery[OrderedProducts]
  schemas ++= orderedProducts.schema

  /**
    * Describes a filled [[ProductDetail]] for a given [[OrderedProduct]]
    * @param id an id to identify this filled detail
    * @param orderedProductId the [[OrderedProduct]] this detail is related to
    * @param productDetailId the detail this is the value of
    * @param value the value of the given detail
    */
  case class FilledDetail(id: Option[Int], orderedProductId: Int, productDetailId: Int, value: String)

  private[models] class FilledDetails(tag: Tag) extends Table[FilledDetail](tag, "filled_details") {
    def id = column[Int]("filled_detail_id", O.PrimaryKey, O.AutoInc)
    def orderedProductId = column[Int]("ordered_product_id")
    def productDetailId = column[Int]("product_detail_id")
    def value = column[String]("filled_detail_value")

    def orderedProduct = foreignKey("filled_detail_ordered_product_fk", orderedProductId, orderedProducts)(_.id)
    def productDetail = foreignKey("filled_detail_product_detail_fk", productDetailId, productDetails)(_.id)

    def * =
      (id.?, orderedProductId, productDetailId, value).shaped <> (FilledDetail.tupled, FilledDetail.unapply)
  }

  private[models] val filledDetails = TableQuery[FilledDetails]
  schemas ++= filledDetails.schema

  /**
    * Represents the base template for a ticket
    * @param id the id of this template (the id 0 is reserved for the default template)
    * @param baseImage the path to the base image of this template, relative to some directory (probably uploads/ or
    *                  something like that)
    *
    * @param barCodeX the x position of the top-left corner of the barcode (0 is the leftmost position)
    * @param barCodeY the x position of the top-left corner of the barcode (0 is the 1st line of the image)
    * @param barCodeWidth the width of the barcode
    * @param barCodeHeight the height of the barcode, if it's higher than the width the barcode will be vertical
    */
  case class TicketTemplate(id: Option[Int], baseImage: String, barCodeX: Int, barCodeY: Int, barCodeWidth: Int, barCodeHeight: Int)

  private[models] class TicketTemplates(tag: Tag) extends Table[TicketTemplate](tag, "ticket_templates") {
    def id = column[Int]("ticket_template_id", O.PrimaryKey, O.AutoInc)
    def baseImage = column[String]("ticket_template_base_image", O.SqlType("VARCHAR(250)"))
    def barCodeX = column[Int]("ticket_template_barcode_x")
    def barCodeY = column[Int]("ticket_template_barcode_y")
    def barCodeWidth = column[Int]("ticket_template_barcode_width")
    def barCodeHeight = column[Int]("ticket_template_barcode_height")

    def * =
      (id.?, baseImage, barCodeX, barCodeY, barCodeWidth, barCodeHeight).shaped <> (TicketTemplate.tupled, TicketTemplate.unapply)
  }

  private[models] val ticketTemplates = TableQuery[TicketTemplates]
  schemas ++= ticketTemplates.schema

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

  private[models] class TicketTemplateComponents(tag: Tag) extends Table[TicketTemplateComponent](tag, "ticket_template_components") {
    def id = column[Int]("ticket_template_component_id", O.PrimaryKey, O.AutoInc)
    def templateId = column[Int]("ticket_template_id")
    def x = column[Int]("ticket_template_component_x")
    def y = column[Int]("ticket_template_component_y")
    def font = column[String]("ticket_template_component_font", O.SqlType("VARCHAR(250)"))
    def fontSize = column[Int]("ticket_template_component_font_size")
    def content = column[String]("ticket_template_component_content")

    def template = foreignKey("ticket_template_components_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * =
      (id.?, templateId, x, y, font, fontSize, content).shaped <> (TicketTemplateComponent.tupled, TicketTemplateComponent.unapply)
  }

  private[models] val ticketTemplateComponents = TableQuery[TicketTemplateComponents]
  schemas ++= ticketTemplateComponents.schema


  private[models] class TicketTemplatesByProduct(tag: Tag) extends Table[(Int, Int)](tag, "ticket_templates_by_product") {
    def templateId = column[Int]("ticket_template_id")
    def template = foreignKey("ticket_templates_by_product_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def productId = column[Int]("product_id")
    def product = foreignKey("ticket_templates_by_product_product_fk", productId, products)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (templateId, productId)

    def pk = primaryKey("pk_ticket_templates_by_product", (templateId, productId))
  }

  private[models] class TicketTemplatesByCategory(tag: Tag) extends Table[(Int, Int)](tag, "ticket_templates_by_category") {
    def templateId = column[Int]("ticket_template_id")
    def template = foreignKey("ticket_templates_by_category_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def categoryId = column[Int]("category_id")
    def category = foreignKey("ticket_templates_by_category_category_fk", categoryId, categories)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (templateId, categoryId)

    def pk = primaryKey("pk_ticket_templates_by_category", (templateId, categoryId))
  }

  private[models] class TicketTemplatesByEvent(tag: Tag) extends Table[(Int, Int)](tag, "ticket_templates_by_event") {
    def templateId = column[Int]("ticket_template_id")
    def template = foreignKey("ticket_templates_by_event_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def eventId = column[Int]("event_id")
    def event = foreignKey("ticket_templates_by_event_event_fk", eventId, events)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * = (templateId, eventId)

    def pk = primaryKey("pk_ticket_templates_by_event", (templateId, eventId))
  }

  private[models] val templatesByProduct = TableQuery[TicketTemplatesByProduct]
  private[models] val templatesByCategory = TableQuery[TicketTemplatesByCategory]
  private[models] val templatesByEvent = TableQuery[TicketTemplatesByEvent]

  schemas ++= templatesByProduct.schema ++ templatesByCategory.schema ++ templatesByEvent.schema

  private[models] class Permissions(tag: Tag) extends Table[(Int, String)](tag, "permissions") {
    def userId = column[Int]("client_id")
    def permission = column[String]("permission", O.SqlType("VARCHAR(180)"))


    def user = foreignKey("permissions_client_fk", userId, clients)(_.id, onDelete = ForeignKeyAction.Cascade)

    def * = (userId, permission)

    def pk = primaryKey("pk_permissions", (userId, permission))
  }

  private[models] val permissions = TableQuery[Permissions]
  schemas ++= permissions.schema

}

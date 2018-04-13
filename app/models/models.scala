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

  private[models] class Categories(tag: Tag) extends Table[Category](tag, "categories") {
    def id = column[Int]("category_id", O.PrimaryKey, O.AutoInc)
    def eventId = column[Int]("event_id")
    def name = column[String]("category_name", O.SqlType("VARCHAR(250)"))
    def isTicket = column[Boolean]("category_is_ticket")

    def event = foreignKey("category_event_fk", eventId, events)(_.id)

    def * =
      (id.?, eventId, name, isTicket).shaped <> (Category.tupled, Category.unapply)
  }

  private[models] val categories = TableQuery[Categories]

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

}

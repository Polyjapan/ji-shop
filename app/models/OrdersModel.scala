package models

import data.{Event, Order, OrderedProduct}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class OrdersModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  def createOrder(order: Order): Future[Int] = db.run((orders returning orders.map(_.id)) += order)

  def orderProducts(ordered: Iterable[OrderedProduct]): Future[Option[Int]] = db.run(orderedProducts ++= ordered)
}

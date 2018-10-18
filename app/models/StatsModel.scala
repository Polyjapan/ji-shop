package models

import java.sql.Timestamp

import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.MySQLProfile
import slick.lifted.QueryBase

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class StatsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  type OrdersFilter = Query[Orders, data.Order, Seq] => Query[Orders, data.Order, Seq]

  def getEvents: Future[Seq[data.Event]] =
    db.run(events.result)

  private val confirmedOrders: Query[Orders, data.Order, Seq] = orders
    .filter(order => order.paymentConfirmed.isDefined)

  private def statsRequest(filter: OrdersFilter) =
    filter(confirmedOrders)
    .join(orderedProducts).on((order, orderedProduct) => orderedProduct.orderId === order.id).map { case (order, orderedProduct) => (order.source, orderedProduct) }
    .join(products).on((pair, product) => pair._2.productId === product.id).map { case ((source, orderedProduct), product) => (product, (source, orderedProduct.paidPrice)) }

  private def eventFilteredStatsRequest(event: Int, ordersFilter: OrdersFilter) =
    statsRequest(ordersFilter).filter { case (product, _) => product.eventId === event }


  def getStats(event: Int, start: Long, end: Long): Future[Seq[(data.Product, Map[data.Source, SalesData])]] = {
    def addStartFilter(prev: OrdersFilter): OrdersFilter =
      if (start == 0) prev
      else
        // even though we know the payment timestamp is not null, we still need to provide a default value
        q => prev(q).filter(order => order.paymentConfirmed.getOrElse(new Timestamp(0)) > new Timestamp(start))

    def addEndFilter(prev: OrdersFilter): OrdersFilter =
      if (end == 0) prev
      else q => prev(q).filter(order => order.paymentConfirmed.getOrElse(new Timestamp(0)) < new Timestamp(end))

    db.run(
      eventFilteredStatsRequest(event, addStartFilter(addEndFilter(q => q))).result
    ).map(data => {
      data
        .groupBy { case (product, _) => product }
        .map {
          case (product, seq) =>
            (product, seq.map(_._2).groupBy { case (source, _) => source }.mapValues(seq => seq.map(_._2)).map {
              case (source, prices) => (source, SalesData(prices.size, prices.sum))
            })
        }.toSeq


    })
  }


}

case class SalesData(amountSold: Int, moneyGenerated: Double)

object SalesData {
  implicit val format: OFormat[SalesData] = Json.format[SalesData]
}

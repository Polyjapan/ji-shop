package models

import java.sql.Timestamp

import data.{Card, Cash}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class StatsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  type OrdersFilter = Query[Orders, data.Order, Seq] => Query[Orders, data.Order, Seq]

  private val confirmedOrders: Query[Orders, data.Order, Seq] = orders
    .filter(order => order.paymentConfirmed.isDefined && !order.removed)

  private def statsRequest(filter: OrdersFilter) =
    filter(confirmedOrders)
      .joinLeft(posPaymentLogs).on((order, log) => order.id === log.orderId && log.accepted === true)
      .join(orderedProducts).on((order, orderedProduct) => orderedProduct.orderId === order._1.id).map { case ((order, log), orderedProduct) => (order.source, orderedProduct, log.map(_.paymentMethod)) }
      .join(products).on((pair, product) => pair._2.productId === product.id).map { case ((source, orderedProduct, log), product) => (product, (source, orderedProduct.paidPrice, log)) }

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
            (product, seq.map(_._2).groupBy { case (source, _, _) => source }.mapValues(seq => seq.map{ case (_, amt, log) => (amt, log) }).map {
              case (source, pricesAndLog) =>
                val globalSum = pricesAndLog.map(_._1).sum
                val sumBy = pricesAndLog.groupBy(_._2)
                  .filterKeys(_.isDefined)
                  .map { case (key, sq) => (key.get, Some(sq.map(_._1).sum)) }
                  .withDefaultValue(None)

                (source, SalesData(pricesAndLog.size, pricesAndLog.map(_._1).sum, sumBy(Cash), sumBy(Card)))
            })
        }.toSeq


    })
  }


}

case class SalesData(amountSold: Int, moneyGenerated: Double, moneyGeneratedCash: Option[Double], moneyGeneratedCard: Option[Double])

object SalesData {
  implicit val format: OFormat[SalesData] = Json.format[SalesData]
}

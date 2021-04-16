package models

import java.sql.Timestamp
import java.text.SimpleDateFormat

import data.{Card, Cash, OnSite}
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

  def getSalesStats(event: Int, group: Int = 24) = {
    val groupBy = group * 3600 * 1000

    def splitStats(seq: Seq[(data.Source, Timestamp)], start: Long, end: Long) = {
      val numbers = seq.map(_._2)
        .groupBy(time => (time.getTime / groupBy) * groupBy)
        .mapValues(times => times.size)

      (start.to(end, groupBy) map (t => (t, numbers.getOrElse(t, 0)))).toList
    }

    db.run(
      products.filter(p => p.eventId === event && p.isTicket)
        .join(orderedProducts).on((p, o) => o.productId === p.id)
        .join(orders).on((po, o) => o.id === po._2.orderId && !o.removed)
        .filter(po => po._2.paymentConfirmed.nonEmpty)
        .map {
          case (_, order) => (order.source, order.paymentConfirmed.get)
        }
        .result
    ).map(seq => {
      val times = seq.map(_._2.getTime)
      val (min, max) = ((times.min / groupBy) * groupBy, (times.max / groupBy) * groupBy)

      val map = seq
        .groupBy(_._1) // group by source
        .view.mapValues(seq => splitStats(seq, min, max))

      // val total = splitStats(seq)

      // (total, map)
      map.toMap
    })
  }

  private def eventFilteredStatsRequest(event: Int, ordersFilter: OrdersFilter) =
    statsRequest(ordersFilter).filter { case (product, _) => product.eventId === event }

  private def addStartFilter(start: Long, prev: OrdersFilter): OrdersFilter =
    if (start == 0) prev
    else
    // even though we know the payment timestamp is not null, we still need to provide a default value
      q => prev(q).filter(order => order.paymentConfirmed.getOrElse(new Timestamp(0)) > new Timestamp(start))

  private def addEndFilter(end: Long, prev: OrdersFilter): OrdersFilter =
    if (end == 0) prev
    else q => prev(q).filter(order => order.paymentConfirmed.getOrElse(new Timestamp(0)) < new Timestamp(end))

  private def addSourceFilter(source: Option[data.Source], prev: OrdersFilter): OrdersFilter =
    if (source.isEmpty) prev
    else q => prev(q).filter(order => order.source === source.get)

  /**
    * Gets some stats to export as CSV
    *
    * @param event the event to get the stats for
    * @param start the start of the period you're interested in (0 = no start)
    * @param end   the end of the period you're interested in (0 = no end)
    * @return a list of comma separated values
    */
  def getOrdersStats(event: Int, start: Long, end: Long, source: Option[data.Source] = None): Future[List[String]] = {
    val filter = addStartFilter(start, addEndFilter(end, addSourceFilter(source, q => q)))

    val dateFormat = new SimpleDateFormat("y-M-d")

    val linesReq =
      filter(confirmedOrders)
        .join(orderedProducts).on(_.id === _.orderId)
        .join(products).on(_._2.productId === _.id)
        .filter(_._2.eventId === event)
        .map(tuple => (tuple._1._1, tuple._1._2, tuple._2.name)) // remove products from the result as we only needed them to filter the event
        .sortBy(_._1.id) // sort by orderId
        .result

    db.run(linesReq).map(seq =>
      "id,enterDate,paymentDate,ticketsPrice,totalPrice,productId,productName,paidPrice,orderType" :: seq.toList.map {
        case (data.Order(Some(id), _, ticketsPrice, totalPrice, Some(paymentDate), Some(enterDate), orderSource, _),
        data.OrderedProduct(_, productId, _, paidPrice), prodName) =>
          id + "," + dateFormat.format(enterDate) + "," + dateFormat.format(paymentDate) + "," + ticketsPrice + "," + totalPrice + "," + productId + "," + prodName + "," + paidPrice + "," + orderSource
      }
    )
  }

  def getStats(event: Int, start: Long, end: Long): Future[Seq[(data.Product, Map[data.Source, SalesData])]] = {
    db.run(
      eventFilteredStatsRequest(event, addStartFilter(start, addEndFilter(end, q => q))).result
    ).map(data => {
      data
        .groupBy { case (product, _) => product }
        .map {
          case (product, seq) =>
            (product, seq.map(_._2).groupBy { case (source, _, _) => source }.view.mapValues(seq => seq.map { case (_, amt, log) => (amt, log) }).map {
              case (source, pricesAndLog) =>
                val globalSum = pricesAndLog.map(_._1).sum
                val sumBy = pricesAndLog.groupBy(_._2)
                  .view
                  .filterKeys(_.isDefined)
                  .map { case (key, sq) => (key.get, Some(sq.map(_._1).sum)) }
                  .toMap
                  .withDefaultValue(None)

                (source, SalesData(pricesAndLog.size, globalSum, sumBy(Cash), sumBy(Card)))
            }.toMap)
        }
        .toSeq
    })
  }


}

case class SalesData(amountSold: Int, moneyGenerated: Double, moneyGeneratedCash: Option[Double], moneyGeneratedCard: Option[Double])

object SalesData {
  implicit val format: OFormat[SalesData] = Json.format[SalesData]
}

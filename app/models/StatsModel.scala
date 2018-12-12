package models

import java.sql.Timestamp
import java.text.SimpleDateFormat

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

    val dateFormat = new SimpleDateFormat("Y-M-d")

    val linesReq =
      filter(confirmedOrders)
        .join(orderedProducts).on(_.id === _.orderId)
        .join(products).on(_._2.productId === _.id)
        .filter(_._2.eventId === event)
        .map(tuple => (tuple._1._1, tuple._1._2, tuple._2.name)) // remove products from the result as we only needed them to filter the event
        .sortBy(_._1.id) // sort by orderId
        .result

    db.run(linesReq).map(seq =>
      "id,enterDate,paymentDate,ticketsPrice,totalPrice,productId,productName,paidPrice" :: seq.toList.map {
        case (data.Order(Some(id), _, ticketsPrice, totalPrice, Some(paymentDate), Some(enterDate), _, _),
        data.OrderedProduct(_, productId, _, paidPrice), prodName) =>
          id + "," + dateFormat.format(enterDate) + "," + dateFormat.format(paymentDate) + "," + ticketsPrice + "," + totalPrice + "," + productId + "," + prodName + "," + paidPrice
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
            (product, seq.map(_._2).groupBy { case (source, _, _) => source }.mapValues(seq => seq.map { case (_, amt, log) => (amt, log) }).map {
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

package controllers.admin

import constants.Permissions._
import data.Source
import javax.inject.Inject
import models.{OrdersModel, ProductsModel, SalesData, StatsModel}
import play.api.Configuration
import play.api.libs.json.{Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.AuthenticationPostfix._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class StatsController @Inject()(cc: ControllerComponents, orders: OrdersModel, stats: StatsModel, products: ProductsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {

  def getSales(event: Int, start: Long = 0, end: Long = 0): Action[AnyContent] = Action.async {
    stats.getStats(event, start, end).map(e => mapStats(e))
  } requiresPermission ADMIN_VIEW_STATS

  def getOrdersStats(event: Int, start: Long = 0, end: Long = 0, source: Option[String]): Action[AnyContent] = Action.async {
    stats.getOrdersStats(event, start, end, source.flatMap(s => Source.asOpt(s))).map(list => Ok(list.mkString("\n")))
  } requiresPermission ADMIN_VIEW_STATS

  def getEntranceStats(event: Int, groupBy: Int = 60): Action[AnyContent] = Action.async {
    stats.getEntranceStats(event, groupBy).map { case (sold, scanned) => Ok(Json.obj("sold" -> sold, "scanned" -> scanned))}
  } requiresPermission ADMIN_VIEW_STATS

  private def mapStats(resp: Seq[(data.Product, Map[data.Source, SalesData])]): Result =
    Ok(Json.toJson(
      // Map the Source to a String before JSon formatting to build a pretty map
      resp.map { case (product, map) => SalesReturn(product, map.map { case (source, value) => (Source.unapply(source), value) }) }
    ))


  case class SalesReturn(product: data.Product, salesData: Map[String, SalesData])

  object SalesReturn {
    implicit val format: OFormat[SalesReturn] = Json.format[SalesReturn]
  }

}

package controllers.admin

import constants.Permissions
import constants.results.Errors._
import data.{AuthenticatedUser, Source}
import javax.inject.Inject
import models.{OrdersModel, ProductsModel, SalesData, StatsModel}
import pdi.jwt.JwtSession._
import play.api.libs.json.{Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class StatsController @Inject()(cc: ControllerComponents, orders: OrdersModel, stats: StatsModel, products: ProductsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {

  def getEvents: Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_VIEW_STATS)) noPermissions.asFuture
    else {
      stats.getEvents.map(e => Ok(Json.toJson(e)))
    }
  }
  }

  def getSales(event: Int, start: Long = 0, end: Long = 0): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_VIEW_STATS)) noPermissions.asFuture
    else {
      stats.getStats(event, start, end).map(e => mapStats(e))
    }
  }
  }

  private def mapStats(resp: Seq[(data.Product, Map[data.Source, SalesData])]): Result =
    Ok(Json.toJson(
      // Map the Source to a String before JSon formatting to build a pretty map
      resp.map { case (product, map) => SalesReturn(product, map.map { case (source, value) => (Source.unapply(source), value)})}
    ))


  case class SalesReturn(product: data.Product, salesData: Map[String, SalesData])

  object SalesReturn {
    implicit val format: OFormat[SalesReturn] = Json.format[SalesReturn]
  }

}

package controllers.shop

import constants.Permissions
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.{OrdersModel, ProductsModel}
import pdi.jwt.JwtSession._
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import services.{PolybankingClient, TicketGenerator}
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ItemsController @Inject()(cc: MessagesControllerComponents, pdfGen: TicketGenerator, orders: OrdersModel, products: ProductsModel, mailerClient: MailerClient, config: Configuration, pb: PolybankingClient)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {
  private def sqlGetItems(getter: ProductsModel => Future[Map[Event, Seq[data.Product]]]): Future[Result] =
    getter(products).map(data => {
      val common = data.mapValues(_.partition(_.isTicket))
      val tickets = common.mapValues(_._1)
      val goodies = common.mapValues(_._2)

      val json = Json.obj("tickets" -> products.buildItemList(tickets), "goodies" -> products.buildItemList(goodies))

      Ok(json)
    })

  /**
    * Get the items available to buy
    */
  def getItems: Action[AnyContent] = Action.async {
    sqlGetItems(_.getProducts)
  }

  /**
    * Get all the items in the visible editions, even if they are not visible to the public<br>
    * These items will not be allowed to appear on a non-web order
    */
  def getAllItems: Action[AnyContent] = Action.async { implicit request => {
    request.jwtSession.getAs[AuthenticatedUser]("user") match {
      case Some(user) if user.hasPerm(Permissions.SEE_INVISIBLE_ITEMS) => sqlGetItems(_.getProductsAdmin)
      case _ => noPermissions.asFuture
    }
  }
  }

  /**
    * Get all the items in all the editions, even if they are not visible to the public
    */
  def getInvisibleItems: Action[AnyContent] = Action.async { implicit request => {
    request.jwtSession.getAs[AuthenticatedUser]("user") match {
      case Some(user) if user.hasPerm(Permissions.SEE_INVISIBLE_ITEMS) => sqlGetItems(_.getAllProducts)
      case _ => noPermissions.asFuture
    }
  }
  }

}

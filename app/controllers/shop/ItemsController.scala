package controllers.shop

import constants.Permissions._
import data._
import javax.inject.Inject
import models.{OrdersModel, ProductsModel}
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import services.{PolybankingClient, PdfGenerationService}
import utils.AuthenticationPostfix._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ItemsController @Inject()(cc: MessagesControllerComponents, pdfGen: PdfGenerationService, orders: OrdersModel, products: ProductsModel, mailerClient: MailerClient, pb: PolybankingClient)(implicit ec: ExecutionContext, config: Configuration) extends MessagesAbstractController(cc) with I18nSupport {
  private def itemsAsResult(getter: ProductsModel => Future[Map[Event, Seq[data.Product]]]): Future[Result] =
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
    itemsAsResult(_.getProducts)
  }

  /**
    * Get all the items in the visible editions, even if they are not visible to the public<br>
    * These items will not be allowed to appear on a non-web order
    */
  def getAllItems: Action[AnyContent] = Action.async {
    itemsAsResult(_.getProductsAdmin)
  } requiresPermission SEE_INVISIBLE_ITEMS

  /**
    * Get all the items in all the editions, even if they are not visible to the public
    */
  def getInvisibleItems: Action[AnyContent] = Action.async {
    itemsAsResult(_.getAllProducts)
  } requiresPermission SEE_INVISIBLE_ITEMS

}

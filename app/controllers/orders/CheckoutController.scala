package controllers.orders


import constants.results.Errors._
import constants.{ErrorCodes, Permissions}
import data._
import exceptions.OutOfStockException
import javax.inject.Inject
import models.{OrdersModel, ProductsModel}
import play.api.Configuration
import play.api.data.FormError
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import services.PolybankingClient
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class CheckoutController @Inject()(cc: ControllerComponents, orders: OrdersModel, products: ProductsModel, pb: PolybankingClient)(implicit ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {
  /**
    * Execute an order
    *
    * @return
    */
  def checkout: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.asOpt[CheckedOutOrder] match {
      case None | Some(CheckedOutOrder(Seq(), _)) => BadRequest.asError(ErrorCodes.NO_REQUESTED_ITEM).asFuture
      case Some(order) => parseOrder(order, request.user)
    }
  }.requiresAuthentication

  private def checkPermissions(source: Option[Source], user: AuthenticatedUser): Boolean = source match {
    case None | Some(Web) => true
    case Some(OnSite) => false // use dedicated endpoint!
    case Some(Reseller) => false // use dedicated endpoint!
    case Some(Gift) if user.hasPerm(Permissions.GIVE_FOR_FREE) => true
    case Some(Physical) if user.hasPerm(Permissions.SELL_IN_ADVANCE) => true
    case _ => false
  }

  /**
    * Compares a user sent order with the database versions of the products, and returns an order that can safely be
    * processed regarding pricings
    *
    * @param items   the items ordered by the user
    * @param dbItems the database version of the items
    * @param source  the order source (if source is not [[Web]] or [[OnSite]], the user will be given fullrights on the prices applied)
    * @return a map of the product and their corresponding checkedOutItem
    */
  private def sanitizeInput(items: Map[Int, Seq[CheckedOutItem]], dbItems: Seq[data.Product], source: Source): Map[Product, Seq[CheckedOutItem]] =
    dbItems.map(p => (p, items.get(p.id.get))).toMap // map the DB product to the one in the order
      .filter { case (_, Some(seq)) if seq.nonEmpty => true; case _ => false } // remove the DB products that are not in the order
      .mapValues(s => s.get) // we get the option
      .toSeq
      .flatMap { case (product, checkedOutItems) => checkedOutItems.map(item => (product, item)) } // we create pairs
      .map { // Check the item prices
      case (product, coItem) if source == Gift => (product, coItem.copy(itemPrice = Some(0D))) // in case of gift, it's free
      case pair@_ if source == Reseller => pair // if imported, we leave the provided price
      case (product, coItem) if product.freePrice => // if the product has a freeprice, we check that user input is > than the freeprice
        if (coItem.itemPrice.isEmpty || coItem.itemPrice.get < product.price) (product, coItem.copy(itemPrice = Some(product.price)))
        else (product, coItem)
      case (product, coItem) => (product, coItem.copy(itemPrice = Some(product.price)))
      // if price is not free, we replace with db price, just in case
    }
      .map {
        // round the prices to 2 decimals
        case (product, coItem) => (product, coItem.copy(itemPrice = coItem.itemPrice.map(d => math.round(d * 100) / 100D)))
      }
      .groupBy(_._1).mapValues(_.map(_._2)) // change the output form

  /**
    * Check that the items in the order are available, i.e. that they exist and that there are sufficient quantities remaining
    *
    * @param items  the items ordered by the user
    * @param map    the map of sanitized products from the database
    * @param source the order source (if source is not [[Web]] the quantities won't be checked)
    * @return the same map
    * @throws NoSuchElementException if an item in the order isn't available
    * @throws OutOfStockException    if an item in the order is not available in sufficient quantity
    */
  private def checkItemAvailability(items: Map[Int, Seq[CheckedOutItem]], map: Map[Product, Seq[CheckedOutItem]], source: Source) = {
    val byId = map.flatMap { case (_, coItems) => coItems.map(_.itemId) }.toSet // Create a set of all item ids

    if (items.forall(i => byId(i._1))) { // Check that all items in the order correspond to available items
      if (source != Web) map // if source is not web, we don't check quantities
      else {
        val oos = map.filter {
          case (product, coItems) => product.maxItems >= 0 && // this item needs stock
            product.maxItems < coItems.map(_.itemAmount).sum // we ordered more than max allowed
        }.keys // Get all the out of stock items
        if (oos.isEmpty) map // Check that all the items are available
        else throw OutOfStockException(oos)
      }
    } else throw new NoSuchElementException
  }

  /**
    * Get the result from the database insert, insert the products, and generate a Result
    *
    * @param result the result of the database insert of the order
    * @return a [[Result]]
    */
  private def generateResult(source: Source)(result: (Iterable[CheckedOutItem], Int, Double)): Future[Result] = result match {
    case (list: Iterable[CheckedOutItem], orderId: Int, totalPrice: Double) =>
      orders.insertProducts(list, orderId).flatMap(success => {
        if (success) {
          // TODO: the client should check that the returned ordered list contains the same items that the one requested
          // If the user comes from the site, we generate a payment link and make him pay
          if (source == Web) pb.startPayment(totalPrice, orderId, list).map {
            case (true, url) =>
              // Insert log
              orders.insertLog(orderId, "payment_start", "polybankingUrl=" + url)
              Ok(Json.obj("ordered" -> list, "success" -> true, "redirect" -> url))
            case (false, err) =>
              orders.insertLog(orderId, "payment_start_fail", "error=" + err)
              InternalServerError.asError(ErrorCodes.POLYBANKING(err))
          }
          else Future(Ok(Json.obj("ordered" -> list, "success" -> true, "orderId" -> orderId)))

        } else dbError.asFuture
      })

  }


  private def parseOrder(order: CheckedOutOrder, user: AuthenticatedUser): Future[Result] = {
    // Check that the user can post the order
    if (!checkPermissions(order.orderType, user))
      return noPermissions.asFuture

    val items = order.items.groupBy(_.itemId)
    val source = order.orderType.getOrElse(Web)

    products.getMergedProducts(source != Web) // get all the products in database, including the hidden ones if the source is not _Web_
      .map(sanitizeInput(items, _, source)) // sanitize the user input using the database
      .map(checkItemAvailability(items, _, source)) // check that items are available
      .flatMap(orders.postOrder(user, _, source))
      .flatMap(generateResult(source))
      .recover {
        case OutOfStockException(items) =>
          NotFound.asFormErrorSeq(
            Seq(
              FormError("", ErrorCodes.OUT_OF_STOCK,
                items.map(it => Json.obj("itemId" -> it.id, "itemName" -> it.name)).toSeq) // return a list of items missing in the error
            ))
        case _: NoSuchElementException =>
          NotFound.asError(ErrorCodes.MISSING_ITEM)
        case _: Throwable =>
          unknownError
      }
  }

}

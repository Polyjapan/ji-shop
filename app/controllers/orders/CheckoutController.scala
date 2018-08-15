package controllers.orders

import java.sql.Timestamp

import constants.{ErrorCodes, Permissions}
import constants.results.Errors._
import data._
import exceptions.OutOfStockException
import javax.inject.Inject
import models.{OrdersModel, ProductsModel}
import pdi.jwt.JwtSession._
import play.api.data.FormError
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import services.PolybankingClient
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class CheckoutController @Inject()(cc: ControllerComponents, orders: OrdersModel, products: ProductsModel, pb: PolybankingClient)(implicit ec: ExecutionContext) extends AbstractController(cc) {
  /**
    * Execute an order
    *
    * @return
    */
  def checkout: Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.jwtSession.getAs[AuthenticatedUser]("user"), request.body.asOpt[CheckedOutOrder]) match {
      case (None, _) => notAuthenticated.asFuture
      case (_, None) | (_, Some(Seq())) => BadRequest.asError(ErrorCodes.NO_REQUESTED_ITEM).asFuture
      case (Some(user), Some(order)) => parseOrder(order, user)
    }
  }

  private def checkPermissions(source: Option[Source], user: AuthenticatedUser): Boolean = source match {
    case None | Some(Web) => true
    case Some(OnSite) if user.hasPerm(Permissions.SELL_ON_SITE) => true
    case Some(Reseller) if user.hasPerm(Permissions.IMPORT_EXTERNAL) => true
    case Some(Gift) if user.hasPerm(Permissions.GIVE_FOR_FREE) => true
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
    * Create an order, then insert it in the database, and return its id as well as the total order price
    *
    * @param map  the sanitized & checked map of the order
    * @param user the user making the request
    * @return a future holding all the [[CheckedOutItem]], as well as the inserted order ID and the total price
    */
  private def postOrder(user: AuthenticatedUser, map: Map[Product, Seq[CheckedOutItem]], source: Source): Future[(Iterable[CheckedOutItem], Int, Double)] = {
    def sumPrice(list: Iterable[CheckedOutItem]) = list.map(p => p.itemPrice.get * p.itemAmount).sum

    val ticketsPrice = sumPrice(map.filter{ case (product, _) => product.isTicket }.values.flatten)
    val totalPrice = sumPrice(map.values.flatten)

    val order =
      if (source == Gift) Order(Option.empty, user.id, 0D, 0D, source = Gift)
      // If the source is a reseller or onsite, then the order has already been paid and we don't want to generate tickets, so we set the paymentConfirmed date
      else if (source == Reseller || source == OnSite) Order(Option.empty, user.id, ticketsPrice, totalPrice, source = source, paymentConfirmed = Some(new Timestamp(System.currentTimeMillis())))
      else Order(Option.empty, user.id, ticketsPrice, totalPrice, source = source)

    orders.createOrder(order).map((map.values.flatten, _, totalPrice))
  }

  /**
    * Get the result from the database insert, insert the products, and generate a Result
    *
    * @param result the result of the database insert of the order
    * @return a [[Result]]
    */
  private def generateResult(result: (Iterable[CheckedOutItem], Int, Double)): Future[Result] = result match {
    case (list: Iterable[CheckedOutItem], orderId: Int, totalPrice: Double) =>

      // Create a list of [[OrderedProduct]] to insert
      val items = list.flatMap(coItem =>
        for (i <- 1 to coItem.itemAmount) // Generate as much ordered products as the quantity requested
          yield OrderedProduct(Option.empty, coItem.itemId, orderId, coItem.itemPrice.get)
      )

      // Order them
      orders.orderProducts(items).flatMap {
        case Some(v) if v >= items.size =>
          // TODO: the client should check that the returned ordered list contains the same items that the one requested
          pb.startPayment(totalPrice, orderId, list).map {
            case (true, url) =>
              Ok(Json.obj("ordered" -> list, "success" -> true, "redirect" -> url))
            case (false, err) =>
              InternalServerError.asError(ErrorCodes.POLYBANKING(err))
          }
        case _ => dbError.asFuture
      }
  }

  /**
    * Get the result from the database insert, insert the products, and generate a Result
    *
    * @param result the result of the database insert of the order
    * @return a [[Result]]
    */
  private def generateResult(result: (Iterable[CheckedOutItem], Int, Double), source: Source): Future[Result] = result match {
    case (list: Iterable[CheckedOutItem], orderId: Int, totalPrice: Double) =>

      // Create a list of [[OrderedProduct]] to insert
      val items = list.flatMap(coItem =>
        for (i <- 1 to coItem.itemAmount) // Generate as much ordered products as the quantity requested
          yield OrderedProduct(Option.empty, coItem.itemId, orderId, coItem.itemPrice.get)
      )

      // Order them
      orders.orderProducts(items).flatMap {
        case Some(v) if v >= items.size =>
          // TODO: the client should check that the returned ordered list contains the same items that the one requested
          // If the user comes from the site, we generate a payment link and make him pay
          if (source == Web) pb.startPayment(totalPrice, orderId, list).map {
            case (true, url) => Ok(Json.obj("ordered" -> list, "success" -> true, "redirect" -> url))
            case (false, err) => InternalServerError.asError(ErrorCodes.POLYBANKING(err))
          }
          else Future(Ok(Json.obj("ordered" -> list, "success" -> true, "orderId" -> orderId)))

        case _ =>
          dbError.asFuture
      }
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
      .flatMap(postOrder(user, _, source))
      .flatMap(generateResult)
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

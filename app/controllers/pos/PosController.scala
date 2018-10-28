package controllers.pos

import constants.results.Errors
import constants.results.Errors._
import constants.{ErrorCodes, Permissions}
import data._
import javax.inject.Inject
import models.{OrdersModel, PosModel, ProductsModel}
import pdi.jwt.JwtSession._
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class PosController @Inject()(cc: ControllerComponents, orders: OrdersModel, model: PosModel, products: ProductsModel)(implicit ec: ExecutionContext, mailerClient: MailerClient) extends AbstractController(cc) {
  private val configForm = Form(mapping("name" -> nonEmptyText)(e => e)(Some(_)))
  private val paymentForm = Form(mapping("name" -> nonEmptyText)(e => e)(Some(_)))

  def getConfigs: Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SELL_ON_SITE)) noPermissions.asFuture
    else {
      model.getConfigs.map(result => Ok(Json.toJson(result)))
    }
  }
  }

  def getConfig(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.SELL_ON_SITE)) noPermissions.asFuture
    else {
      model
        .getFullConfig(id)
        .map {
          case Some(result) => Ok(Json.toJson(result))
          case None => Errors.notFound("id")
        }
    }
  }
  }


  def addProductToConfig(id: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    val addProductForm = Form(
      mapping("productId" -> number, "row" -> number, "col" -> number, "color" -> nonEmptyText, "textColor" -> nonEmptyText)(Tuple5.apply)(Tuple5.unapply))

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.CHANGE_POS_CONFIGURATIONS)) noPermissions.asFuture
    else {
      addProductForm.bindFromRequest().fold(withErrors => {
        formError(withErrors).asFuture // If the name is absent from the request
      }, form => {
        val config = PosConfigItem(id, form._1, form._2, form._3, form._4, form._5)

        model.getConfig(id).flatMap(opt => {
          if (opt.isDefined) {
            if (opt.get._2.exists(e => e.productId == form._1)) success.asFuture // We don't have to insert, it's already there
            else model.addProduct(config).map(r => if (r == 1) success else dbError).recover { case _ => dbError } // insert

          } else notFound("config").asFuture
        })

      })

    }
  }

  def removeProductFromConfig(id: Int): Action[String] = Action.async(parse.text) { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.CHANGE_POS_CONFIGURATIONS)) noPermissions.asFuture
    else {
      try {
        val productId = request.body.toInt

        model.getConfig(id).flatMap(opt => {
          if (opt.isDefined) {
            if (opt.get._2.exists(e => e.productId == productId)) model.removeProduct(id, productId).map(r => if (r == 1) success else dbError).recover { case _ => dbError }
            else success.asFuture // We don't have to remove, it's not there anymore
          } else notFound("config").asFuture
        })
      }
    }
  }
  }

  def createConfig: Action[JsValue] = Action.async(parse.json) { implicit request => {
    handleConfig(config => {
      model.createConfig(config)
        .map(inserted => Ok(Json.toJson(inserted)))
        .recover { case _ => dbError }
    })
  }
  }

  def updateConfig(id: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    handleConfig(config => {
      model.updateConfig(id, config.copy(Some(id)))
        .map(_ => Ok(Json.toJson(id)))
        .recover { case _ => dbError }
    })
  }
  }

  private def handleConfig(saver: PosConfiguration => Future[Result])(implicit request: Request[JsValue]): Future[Result] = {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.CHANGE_POS_CONFIGURATIONS)) noPermissions.asFuture
    else {
      configForm.bindFromRequest().fold(withErrors => {
        formError(withErrors).asFuture // If the name is absent from the request
      }, form => {
        val config = PosConfiguration(None, form)

        saver(config)
      })
    }
  }

  def checkout: Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.jwtSession.getAs[AuthenticatedUser]("user"), request.body.asOpt[CheckedOutOrder]) match {
      case (None, _) => notAuthenticated.asFuture
      case (Some(user), _) if !user.hasPerm(Permissions.SELL_ON_SITE) => noPermissions.asFuture
      case (_, None) | (_, Some(CheckedOutOrder(Seq(), _))) => BadRequest.asError(ErrorCodes.NO_REQUESTED_ITEM).asFuture
      case (Some(user), Some(order)) => parseOrder(order, user)
    }
  }

  def processPayment(orderId: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.jwtSession.getAs[AuthenticatedUser]("user"), request.body.asOpt[PaymentLog]) match {
      case (None, _) => notAuthenticated.asFuture
      case (Some(user), _) if !user.hasPerm(Permissions.SELL_ON_SITE) => noPermissions.asFuture
      case (_, None) => BadRequest.asFuture
      case (Some(user), Some(log)) =>
        // Check order source
        orders.getOrder(orderId)
          .flatMap {
            case Some(Order(_, _, _, _, None, _, OnSite)) =>
              // This is an on-site order
              // We can insert the log

              model.insertLog(log.toDbItem(orderId))
                .flatMap(inserted => {
                  if (log.accepted && inserted > 0) {
                    // We need to mark the order as paid
                    orders.markAsPaid(orderId).map(_ > 0)
                  } else {
                    Future.successful(inserted > 0)
                  }
                })
                .map(succ =>
                  if (succ) Ok.asSuccess
                  else dbError)
            case Some(Order(_, _, _, _, None, _, _)) => BadRequest.asError(ErrorCodes.NOT_ON_SITE).asFuture
            case Some(_) => BadRequest.asError(ErrorCodes.ALREADY_ACCEPTED).asFuture
            case None => notFound("orderId").asFuture
          }
    }
  }

  /*
  Workflow:
   CLIENT: enters order
   CLIENT: sends order (/checkout)
   SERVER: inserts order or REJECT (/checkout)
   CLIENT: sends payment type
   SERVER: inserts payment type

   if cash:
   CLIENT: sends confirmation with details
   SERVER: marks order as paid

   if bank: (if not possible to use directly the client to handle sumup callbacks)
   CLIENT: listens for confirmation
   SERVER: waits for endpoint to be called
   SERVER: stores all bank transaction details

   */

  private def getProducts(items: Map[Int, Seq[CheckedOutItem]], dbItems: Seq[data.Product]): Map[Product, Seq[CheckedOutItem]] =
    dbItems.map(p => (p, items.get(p.id.get))).toMap // map the DB product to the one in the order
      .filter { case (_, Some(seq)) if seq.nonEmpty => true; case _ => false } // remove the DB products that are not in the order
      .mapValues(s => s.get) // we get the option
      .toSeq
      .flatMap { case (product, checkedOutItems) => checkedOutItems.map(item => (product, item)) }
      .groupBy(_._1).mapValues(_.map(_._2))

  private def insertProducts(result: (Iterable[CheckedOutItem], Int, Double)): Future[Result] = result match {
    case (list: Iterable[CheckedOutItem], orderId: Int, totalPrice: Double) =>

      implicit val format: OFormat[OnSiteOrderResponse] = Json.format[OnSiteOrderResponse]

      orders.insertProducts(list, orderId).map(success => {
        if (success) Ok(Json.toJson(OnSiteOrderResponse(orderId, totalPrice)))
        else dbError
      })

  }

  private def parseOrder(order: CheckedOutOrder, user: AuthenticatedUser): Future[Result] = {
    // Check that the user can post the order
    val items = order.items.groupBy(_.itemId)

    products.getMergedProducts(includeHidden = true, includeHiddenEvents = true) // get all the products in database
      .map(getProducts(items, _)) // we create pairs
      .flatMap(orders.postOrder(user, _, OnSite))
      .flatMap(insertProducts)
      .recover {
        case _: NoSuchElementException =>
          NotFound.asError(ErrorCodes.MISSING_ITEM)
        case _: Throwable =>
          unknownError
      }
  }

  case class OnSiteOrderResponse(orderId: Int, price: Double)


}

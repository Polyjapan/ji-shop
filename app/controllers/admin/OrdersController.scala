package controllers.admin

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.Date

import constants.ErrorCodes
import constants.Permissions._
import constants.results.Errors._
import data.{Client, Order, OrderedProduct, Physical, Reseller}
import javax.inject.Inject
import models.OrdersModel
import models.OrdersModel.GeneratedBarCode
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsArray, JsValue, Json, OFormat}
import play.api.libs.mailer.{AttachmentData, MailerClient}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import services.EmailService.{InviteEmail, OrderEmail, TicketsEmail}
import services.{EmailService, PdfGenerationService}
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.collection.mutable
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author zyuiop
 */
class OrdersController @Inject()(cc: ControllerComponents, orders: OrdersModel, pdfGen: PdfGenerationService, mailing: EmailService)(implicit mailerClient: MailerClient, ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {
  private val validationRequest = Form(mapping("orderId" -> number, "targetEmail" -> optional(email))(Tuple2.apply)(Tuple2.unapply))

  private lazy val dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

  /**
   * Force the validation of an order (i.e. bypass IPN). The body should be a json containing the orderId, and an
   * optional targetEmail field that, when present, overrides the destination email and replaces the email content by
   * a sweet invitation message
   */
  def validateOrder: Action[JsValue] = Action.async(parse.json) { implicit request => {
    validationRequest.bindFromRequest.fold(err =>
      formError(err).asFuture, {
      case (orderId, None) =>
        orders.insertLog(orderId, "admin_force_self", "Trying to force-accept order (with no specific target mail)")
        processOrder(orderId, _ => (tickets, client, order) => OrderEmail(order, client, tickets))
      case (orderId, Some(targetEmail)) =>
        processOrder(orderId, {
          case Physical =>
            orders.insertLog(orderId, "admin_force_send", "Sending an order email to " + targetEmail)
            (tickets, client, order) => OrderEmail(order, client, tickets, Some(targetEmail))
          case _ =>
            orders.insertLog(orderId, "admin_force_invite", "Trying to generate an invite for " + targetEmail)
            (tickets, _, _) => InviteEmail(tickets, targetEmail)
        })
    })
  }
  } requiresPermission FORCE_VALIDATION

  def resendEmail(orderId: Int): Action[AnyContent] = Action.async { implicit request => {
    /*orders.getBarcodes(orderId).map {
      case (codes, Some((client, event, order))) if codes.nonEmpty =>
        Future(mailing.sendMail(OrderEmail(order, client, codes, None)))
        Ok.asSuccess
      case (_, None) =>
        notFound("order")
    }*/ ???
  }
  } requiresPermission FORCE_VALIDATION

  case class ImportedItemData(product: Int, barcode: String, paidPrice: Int, date: String, refunded: Boolean)

  object ImportedItemData {
    implicit val format: OFormat[ImportedItemData] = Json.format[ImportedItemData]
  }

  def importOrder(event: Int): Action[Seq[ImportedItemData]] = Action.async(parse.json[Seq[ImportedItemData]]) { implicit request => {


    val log = new mutable.ArrayDeque[String]

    log += "Processing " + request.body.size + " codes..."

    // 0. Filter out refunds
    val (refunded, codes): (Seq[ImportedItemData], Seq[ImportedItemData]) = request.body.partition(_.refunded)

    log ++= refunded map (_.barcode) map (code => "Removing code " + code + " (refund!)")

    log += ""
    log += "-- Removing existing codes among " + codes.size + " remaining codes"
    log += ""

    // 1. Filter out existing codes
    // TODO: rewrite entirely
    /*orders.filterBarcodes*/Future.successful(codes.map(_.barcode)).flatMap(existingCodes => {
      log ++= existingCodes.map(code => s"Removing code $code (already exists)")
      // TODO: we might want to report with a warning codes that already exist AND don't come from a reseller
      // TODO: we might want to report with a warning codes that already exist AND don't have the same product id
      log += ""

      val existingSet = existingCodes.toSet

      val remain = codes
        // Remove existing (end)
        .filterNot(code => existingSet(code.barcode))

      log += s"-- Inserting new orders for the ${remain.size} remaining codes"
      log += ""

      remain
        .sortBy(_.date)
        // Group by date. 1 date = 1 order
        .groupBy(_.date)
        .toList
        .sortBy(_._1)
        .map(_._2)
        // Insert everything
        .map(items => {
          val price = items.map(_.paidPrice).sum
          val time = dateFormat.parse(items.head.date)
          val order = Order(None, request.user.id, price, price, source = Reseller, enterDate = Some(new Timestamp(time.getTime)))

          // Create order
          orders.createOrder(order)
            .flatMap(orderId => {
              // Map imported items to ordered products
              val products = items.map(item => {
                (OrderedProduct(None, item.product, orderId, item.paidPrice), item.barcode)
              })

              // Insert a log and the items of the order
              orders.insertLog(orderId, "import_details", "Order generated from an import on " + dateFormat.format(new Date())).flatMap(_ =>
                orders.fillImportedOrder(products).map(_ => (orderId,
                  "Inserting order " + orderId :: products.map(pair => " . Inserted barcode " + pair._2).toList
                )))
            })
            // Mark the inserted order as paid
            .flatMap(pair => orders.markAsPaid(pair._1, order.enterDate.get).map(_ => pair._2))
            .recover {
              case e: Exception =>
                println("Error while inserting barcodes from import: ")
                e.printStackTrace()
                List("Failed insertion of barcodes " + items.map(_.barcode).mkString(", ") + ": " + e.getMessage)
            }
        })
        // Collect all the generated futures inside a single one
        .reduceOption((left, right) => left.flatMap(leftR => right.map(rightR => leftR ::: rightR)))
        .getOrElse(Future(List.empty[String]))
        .map(result => Ok((log.toList ::: result).mkString("\n")))
        .recover {
          case e: Exception =>
            println("Global error in import: ")
            e.printStackTrace()
            InternalServerError.asError(ErrorCodes.DATABASE)
        }
    })

  }
  } requiresPermission IMPORT_EXTERNAL

  private type MailSender = data.Source => (Seq[GeneratedBarCode], Client, Order) => TicketsEmail

  private def processOrder(orderId: Int, mailGen: MailSender) = {
    orders.acceptOrder(orderId).map {
      case (Seq(), _, _) =>
        orders.insertLog(orderId, "admin_force_duplicate", "Duplicate order validation")
        NotFound.asError(ErrorCodes.ALREADY_ACCEPTED)
      case (tickets, client, order) if tickets.nonEmpty =>
        orders.insertLog(orderId, "admin_force_ok", "Order forcefully accepted", accepted = true)
        mailing.sendMail(mailGen(order.source)(tickets, client, order))

        Ok(Json.obj("success" -> true, "errors" -> JsArray()))
      case _ => dbError
    }
  }


  def getOrders(event: Int): Action[AnyContent] = Action.async {
    orders.ordersByEvent(event, returnRemovedOrders = true).map(seq => Ok(Json.toJson(seq)))
  } requiresPermission ADMIN_ACCESS

  def getOrdersByUser(userId: Int): Action[AnyContent] = Action.async {
    orders.loadOrders(userId, isAdmin = true).map(seq => Ok(Json.toJson(seq)))
  } requiresPermission VIEW_OTHER_ORDER

  def getOrderUserInfo(order: Int): Action[AnyContent] = Action.async {
    orders.userFromOrder(order).map(user => Ok(Json.toJson(user)))
  } requiresPermission ADMIN_ACCESS

  def getPosPaymentLogs(order: Int): Action[AnyContent] = Action.async {
    orders.getPosPaymentLogs(order).map(seq => Ok(Json.toJson(seq)))
  } requiresPermission ADMIN_ACCESS

  def getOrderLogs(order: Int): Action[AnyContent] = Action.async {
    orders.getOrderLogs(order).map(seq => Ok(Json.toJson(seq)))
  } requiresPermission ADMIN_ACCESS

  def removeOrder(order: Int): Action[AnyContent] = Action.async {
    orders.insertLog(order, "admin_remove", "Order was deleted")

    orders.setOrderRemoved(order, removed = true).map(rep => Ok(Json.toJson(rep)))
  } requiresPermission ADMIN_REMOVE_ORDER


}

package controllers.admin

import java.sql.Timestamp
import java.time.Instant

import constants.{ErrorCodes, Permissions}
import constants.emails.OrderEmail
import constants.results.Errors._
import data.{AuthenticatedUser, Order, OrderedProduct, Reseller}
import javax.inject.Inject
import models.OrdersModel.TicketBarCode
import models.{OrdersModel, ProductsModel}
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.libs.mailer.{AttachmentData, Email, MailerClient}
import play.api.mvc.{AbstractController, Action, ControllerComponents, Result}
import services.TicketGenerator
import utils.Implicits._
import pdi.jwt.JwtSession._
import utils.Timings

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class OrdersController @Inject()(cc: ControllerComponents, orders: OrdersModel, pdfGen: TicketGenerator, products: ProductsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {
  private val validationRequest = Form(mapping("orderId" -> number, "targetEmail" -> optional(email))(Tuple2.apply)(Tuple2.unapply))

  /**
    * Force the validation of an order (i.e. bypass IPN). The body should be a json containing the orderId, and an
    * optional targetEmail field that, when present, overrides the destination email and replaces the email content by
    * a sweet invitation message
    */
  def validateOrder: Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.FORCE_VALIDATION)) noPermissions.asFuture
    else validationRequest.bindFromRequest.fold(err =>
      formError(err).asFuture, {
      case (orderId, None) =>
        processOrder(orderId, OrderEmail.sendOrderEmail)
      case (orderId, Some(v)) =>
        processOrder(orderId, sendInviteEmail(v))
    })
  }
  }

  def importOrder(event: Int) = Action.async(parse.text) { implicit request => {
    // Logic of this endpoint:
    // 1. Parse the file and extract the data
    // 2. Extract the item names, check if they exist (or create them), and get their IDs
    // 3. Create an order and get its id
    // 4. Created OrderedProducts with the obtained item IDs
    // 5. Insert the OrderedProducts, along with their barcodes
    // Because we use "DBIO.sequence" in the logic, it's not possible to send the same file twice
    // Any duplicated barcode in the file will cause the whole upload to fail.
    // I don't consider this an issue for now, but we might need to evaluate this

    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.IMPORT_EXTERNAL)) noPermissions.asFuture
    else {
      val lines = request.body.split("\n")

      // Extract the first line to determine the location of the barcode and price
      // We take the head, split it on ";", make everything lower case, and remove any space that might have slipped
      // between around ";"
      val head = lines.head.toLowerCase.split(";").map(_.trim)

      val barcodePos = head.indexOf("code barre")
      val categoryPos = head.indexOf("tarif")
      val pricePos = head.indexOf("prix")

      if (barcodePos == -1 || categoryPos == -1) {
        BadRequest.asError(ErrorCodes.MISSING_FIELDS).asFuture
      } else {
        case class ImportedTicket(barcode: String, category: String, price: Option[Double])

        // Extract the data from the lines
        val data = lines.tail.map(line => {
          val content = line.toLowerCase().split(";").map(_.trim)

          ImportedTicket(content(barcodePos), content(categoryPos),
            Option(pricePos).filter(_ != -1).map(content(_).toDouble))
          // Take the pricePos, and if it's not -1 get the corresponding content as a double
        })

        // Extract the total price
        val price = data.map(_.price).map(_.getOrElse(0D)).sum

        // Extract single categories
        val categories = data.groupBy(_.category).keys

        // We query the items corresponding to these categories, and insert them if they don't exist
        products.getOrInsert(event, categories).flatMap(map => {
          orders.createOrder(Order(None, user.get.id, price, price, Option(Timestamp.from(Instant.now())), source = Reseller)).map(orderId => (map, orderId))
        }).flatMap({
          case (idMap, orderId) =>
            val products = data.map(ticket => {
              (OrderedProduct(None, idMap(ticket.category), orderId, ticket.price.getOrElse(0)), ticket.barcode)
            })

            orders.fillImportedOrder(products).map(_ => Ok).recover{ case _ => InternalServerError.asError(ErrorCodes.DATABASE) }
        })
      }
    }
  }
  }

  def sendInviteEmail(email: String)(attachments: Seq[AttachmentData], client: data.Client)(implicit mailerClient: MailerClient): String =
    mailerClient.send(Email(
      "Vos invitations JapanImpact",
      "Comité JapanImpact <comite@japan-impact.ch>",
      Seq(email),
      bodyText = Some("Bonjour, " +
        "\nLe comité JapanImpact a le plaisir de vous faire parvenir vos invitations à notre événement." +
        "\nVous trouverez en pièce jointe vos billets. Vous pouvez les imprimer ou les présenter sur smartphone." +
        "\n\nCordialement,," +
        "\nLe Comité Japan Impact"),
      attachments = attachments
    ))

  private type MailSender = (Seq[AttachmentData], data.Client) => Any

  private def processOrder(orderId: Int, mailSender: MailSender) = {
    orders.acceptOrder(orderId).map {
      case (Seq(), _) => NotFound.asError(ErrorCodes.ALREADY_ACCEPTED)
      case (oldSeq, client) if oldSeq.nonEmpty =>
        val attachments: Seq[AttachmentData] =
          oldSeq.map(pdfGen.genPdf).map(p => AttachmentData(p._1, p._2, "application/pdf"))


        Future(mailSender(attachments, client))

        Ok(Json.obj("success" -> true, "errors" -> JsArray()))
      case _ => dbError
    }
  }


}

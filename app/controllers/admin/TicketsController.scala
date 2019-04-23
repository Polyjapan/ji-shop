package controllers.admin

import constants.Permissions._
import constants.results.Errors._
import javax.inject.Inject
import models.OrdersModel
import play.api.libs.json.Json
import play.api.libs.mailer.MailerClient
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class TicketsController @Inject()(cc: ControllerComponents, orders: OrdersModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {


  def getTicketData(ticket: String): Action[AnyContent] = Action.async {
    orders.findOrderByBarcode(ticket).flatMap {
      case Some((ticketData, orderId)) =>
        // Get validation status
        orders.getTicketValidationStatus(ticketData.id.get).map {
          case Some((claimedTicket, claimer)) =>
            Json.obj("scannedAt" -> claimedTicket.claimedAt,
              "scannedBy" -> claimer, "scanned" -> true)
          case _ => Json.obj("scanned" -> false)

        }.map(validation => {
          val json = Json.obj(
            "ticket" -> Json.obj(
              "createdAt" -> ticketData.createdAt.get,
              "removed" -> ticketData.removed,
              "id" -> ticketData.id
            ),
            "orderId" -> orderId,
            "validation" -> validation
          )


          Ok(json)
        }
        )

      case None =>
        notFound("barcode").asFuture
    }
  } requiresPermission ADMIN_ACCESS

}

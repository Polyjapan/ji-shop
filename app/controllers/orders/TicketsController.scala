package controllers.orders

import constants.emails.OrderEmail
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.OrdersModel
import pdi.jwt.JwtSession._
import play.api.libs.mailer.{AttachmentData, MailerClient}
import play.api.mvc._
import services.PolybankingClient.CorrectIpn
import services.{PolybankingClient, TicketGenerator}
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class TicketsController @Inject()(cc: ControllerComponents, pdfGen: TicketGenerator, orders: OrdersModel, pb: PolybankingClient)(implicit ec: ExecutionContext, mailerClient: MailerClient) extends AbstractController(cc) {

  def ipn: Action[Map[String, Seq[String]]] = Action.async(parse.formUrlEncoded) { implicit request => {
    pb.checkIpn(request.body) match {
      case CorrectIpn(valid: Boolean, order: Int) =>
        println(s"Processing IPN request for order $order. PostFinance status is $valid")
        if (!valid) BadRequest.asError("error.postfinance_refused").asFuture
        else orders.acceptOrder(order).map {
          case (Seq(), _) => NotFound.asError("error.order_not_found")
          case (oldSeq, client) =>
            val attachments =
              oldSeq.map(pdfGen.genPdf).map(p => AttachmentData(p._1, p._2, "application/pdf"))

            OrderEmail.sendOrderEmail(attachments, client)

            Ok
          case _ => BadRequest.asError("error.already_accepted")
        }
      case a@_ => BadRequest.asError(a.toString).asFuture
    }

  }
  }


  /**
    * Get the PDF ticket for a given barcode
    *
    * @param barCode the barcode searched
    * @return the pdf ticket
    */
  def getTicket(barCode: String) = Action.async { implicit request => {
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      notAuthenticated.asFuture
    else {
      orders.findBarcode(barCode) map {
        case None =>
          NotFound.asError("error.ticket_not_found")
        case Some((code, client: Client)) =>
          if (client.id.get != user.get.id && !user.get.hasPerm("admin.view_other_ticket"))
          // Return the same error as if the ticket didn't exist
          // It avoids leaking information about whether or not a ticket exists
            NotFound.asError("error.ticket_not_found")
          else {
            // Generate the PDF
            Ok(pdfGen.genPdf(code)._2).as("application/pdf")
          }
      }
    }
  }
  }

}

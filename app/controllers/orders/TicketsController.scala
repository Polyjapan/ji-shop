package controllers.orders

import constants.Permissions
import constants.emails.OrderEmail
import constants.results.Errors
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.OrdersModel
import play.api.libs.mailer.{AttachmentData, MailerClient}
import play.api.mvc._
import services.PolybankingClient.CorrectIpn
import services.{PolybankingClient, TicketGenerator}
import utils.AuthenticationPostfix._
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
        if (!valid) {
          orders.insertLog(order, "ipn_refused", "Postfinance refused the order")
          BadRequest.asError("error.postfinance_refused").asFuture
        } else orders.acceptOrder(order).map {
          case (Seq(), _, _) => NotFound.asError("error.order_not_found")
          case (oldSeq, client, _) =>
            val attachments =
              oldSeq.map(pdfGen.genPdf).map(p => AttachmentData(p._1, p._2, "application/pdf"))

            OrderEmail.sendOrderEmail(attachments, client)
            orders.insertLog(order, "ipn_accepted", "IPN was accepted and tickets were generated", accepted =true)

            Ok
          case _ =>
            orders.insertLog(order, "ipn_duplicate", "Duplicate IPN request for order? (or other db error)")
            BadRequest.asError("error.already_accepted")
        }
      case a@_ =>
        println("Wrong IPN request found.")
        println(a.toString)
        println("Request details:")
        println(request.body)
        BadRequest.asError(a.toError).asFuture
    }

  }
  }


  /**
    * Get the PDF ticket for a given barcode
    *
    * @param barCode the barcode searched
    * @return the pdf ticket
    */
  def getTicket(barCode: String) = Action.async { implicit request =>
    orders.findBarcode(barCode) map {
      case None =>
        Errors.notFound()
      case Some((code, client: Client, _)) =>
        if (client.id.get != request.user.id && !request.user.hasPerm(Permissions.VIEW_OTHER_TICKET))
        // Return the same error as if the ticket didn't exist
        // It avoids leaking information about whether or not a ticket exists
          Errors.notFound()
        else {
          // Generate the PDF
          Ok(pdfGen.genPdf(code)._2).as("application/pdf")
        }
    }
  }.requiresAuthentication
}

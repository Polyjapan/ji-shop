package controllers.orders

import constants.Permissions
import constants.results.Errors
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.OrdersModel
import play.api.Configuration
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import services.EmailService.OrderEmail
import services.PolybankingClient.CorrectIpn
import services.{EmailService, PdfGenerationService, PolybankingClient}
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
 * @author zyuiop
 */
class TicketsController @Inject()(cc: ControllerComponents, pdfGen: PdfGenerationService, orders: OrdersModel, pb: PolybankingClient, emails: EmailService)(implicit ec: ExecutionContext, mailerClient: MailerClient, conf: Configuration) extends AbstractController(cc) {

  def ipn: Action[Map[String, Seq[String]]] = Action.async(parse.formUrlEncoded) { implicit request => {
    pb.checkIpn(request.body) match {
      case CorrectIpn(valid: Boolean, order: Int) =>
        println(s"Processing IPN request for order $order. PostFinance status is $valid")
        if (!valid) {
          orders.insertLog(order, "ipn_refused", "Postfinance refused the order")
          BadRequest.asError("error.postfinance_refused").asFuture
        } else orders.acceptOrder(order).flatMap {
          case (Seq(), _, _) => NotFound.asError("error.order_not_found").asFuture
          case (tickets, client, order) =>
            emails.sendMail(OrderEmail(order, client, tickets))
              .map { _ =>
                orders.insertLog(order.id.get, "ipn_accepted", "IPN was accepted and tickets were generated", accepted = true)
                Ok
              }
              .recover {
                case exception: Exception =>
                  orders.insertLog(order.id.get, "ipn_acceptation_fail", "Error while sending the barcodes. " + exception.getMessage, accepted = true)
                  exception.printStackTrace()
                  InternalServerError
              }
          case _ =>
            orders.insertLog(order, "ipn_duplicate", "Duplicate IPN request for order? (or other db error)")
            BadRequest.asError("error.already_accepted").asFuture
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
    ???
  }.requiresAuthentication
}

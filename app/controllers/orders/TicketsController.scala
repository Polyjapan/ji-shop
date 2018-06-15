package controllers.orders

import data._
import javax.inject.Inject
import models.OrdersModel
import pdi.jwt.JwtSession._
import play.api.data.FormError
import play.api.libs.json.Json
import play.api.libs.mailer.{AttachmentData, Email, MailerClient}
import play.api.mvc._
import services.PolybankingClient.CorrectIpn
import services.{PolybankingClient, TicketGenerator}
import utils.Formats._

import scala.concurrent.{ExecutionContext, Future}


/**
  * @author zyuiop
  */
class TicketsController @Inject()(cc: ControllerComponents, pdfGen: TicketGenerator, orders: OrdersModel, mailerClient: MailerClient, pb: PolybankingClient)(implicit ec: ExecutionContext) extends AbstractController(cc) {

  def ipn: Action[Map[String, Seq[String]]] = Action.async(parse.formUrlEncoded) { implicit request => {
    pb.checkIpn(request.body) match {
      case CorrectIpn(valid: Boolean, order: Int) =>
        println(s"Processing IPN request for order $order. PostFinance status is $valid")
        if (!valid) Future(BadRequest)
        else orders.acceptOrder(order).map {
          case (Seq(), _) => NotFound
          case (oldSeq, client) =>
            val attachments =
              oldSeq.map(pdfGen.genPdf).map(p => AttachmentData(p._1, p._2, "application/pdf"))

            mailerClient.send(Email(
              "Vos billets JapanImpact",
              "Billetterie JapanImpact <ticket@japan-impact.ch>",
              Seq(client.email),
              bodyText = Some("Bonjour, " +
                "\nVous avez réalisé des achats sur la boutique JapanImpact et nous vous en remercions." +
                "\nVous trouverez en pièce jointe vos billets. Vous pouvez les imprimer ou les présenter sur smartphone." +
                "\nLes billets sont non nominatifs, mais ils ne peuvent être utilisés qu'une seule fois. Si vous avez un tarif réduit, n'oubliez pas de prendre votre justificatif avec vous, ou il pourrait vous être demandé de payer la différence." +
                "\n\nAvec nos remerciements," +
                "\nL'équipe Japan Impact"),
              attachments = attachments
            ))

            Ok
          case _ => BadRequest
        }
      case a@_ => Future(BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", a.toString)))))
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
      Future(Unauthorized(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.no_auth_token")))))
    else {
      orders.findBarcode(barCode) map {
        case None =>
          NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.ticket_not_found"))))
        case Some((code, client: Client)) =>
          if (client.id.get != user.get.id && !user.get.hasPerm("admin.view_other_ticket"))
          // Return the same error as if the ticket didn't exist
          // It avoids leaking information about whether or not a ticket exists
            NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.ticket_not_found"))))
          else {
            // Generate the PDF
            Ok(pdfGen.genPdf(code)._2).as("application/pdf")
          }
      }
    }
  }
  }

}

package services

import akka.Done
import data.{Event, Order}
import javax.inject.{Inject, Singleton}
import models.OrdersModel
import models.OrdersModel.GeneratedBarCode
import play.api.libs.mailer.{AttachmentData, Email, MailerClient}
import services.EmailService.{OrderEmail, TicketsEmail}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EmailService @Inject()(orders: OrdersModel)(implicit ec: ExecutionContext, pdfGen: PdfGenerationService, mailer: MailerClient) {
  /**
   * Send the provided email
   *
   * @param email the type and content of the email to send
   * @return a future
   */
  def sendMail(email: TicketsEmail): Future[Done] = {
    val attachments = email.generateAttachments

    println("Sending email " + email)

    val send = email match {
      case o@OrderEmail(order, client, _, _) =>
        // Order eMails need an additional attachment: the invoice
        // We therefore generate it here before sending the email
        orders.getOrderProducts(order.id.get) flatMap { products =>
          val (invoiceName, body) = pdfGen.genInvoice(client, o.event, order, products)

          o.sendMail(attachments :+ AttachmentData(invoiceName, body, "application/pdf"))
        }
      case other =>
        other.sendMail(attachments)
    }

    // Log errors and pass them on
    send.recover {
      case t: Throwable =>
        println("Error while sending email " + email)
        t.printStackTrace()
        throw t
    }
  }
}

object EmailService {

  sealed trait TicketsEmail {
    val tickets: Seq[GeneratedBarCode]

    def generateAttachments(implicit pdfGen: PdfGenerationService): Seq[AttachmentData] =
      tickets.map { code =>
        val (name, document) = pdfGen.genPdf(code)
        AttachmentData(name, document, "application/pdf")
      }

    def sendMail(attachments: Seq[AttachmentData])(implicit mailer: MailerClient, ec: ExecutionContext): Future[Done]

    lazy val event: Event = tickets.head.event
  }

  /**
   * An email to confirm that a specific order was accepted
   *
   * @param order   the order to accept
   * @param client  the client that made the order
   * @param tickets the tickets contained in the order
   * @param sendTo  optional: if specified, the email address to which the order should be sent
   */
  case class OrderEmail(order: Order, client: data.Client, tickets: Seq[GeneratedBarCode], sendTo: Option[String] = None) extends TicketsEmail {
    override def sendMail(attachments: Seq[AttachmentData])(implicit mailer: MailerClient, ec: ExecutionContext): Future[Done] = Future {
      mailer.send(Email(
        s"Boutique Japan Impact - Commande ${order.id.get} acceptée",
        "Billetterie JapanImpact <ticket@japan-impact.ch>",
        sendTo.orElse(client.billingEmail).orElse(client.email).toList, // possibly 0 email
        bodyHtml = Some(views.html.emails.orderEmail(client, event, order).body),
        attachments = attachments
      ))
      Done
    }
  }

  /**
   * An email sent to an invited person
   *
   * @param tickets the invite tickets
   * @param sendTo  the email of the invited person
   */
  case class InviteEmail(tickets: Seq[GeneratedBarCode], sendTo: String) extends TicketsEmail {
    override def sendMail(attachments: Seq[AttachmentData])(implicit mailer: MailerClient, ec: ExecutionContext): Future[Done] =
      Future {
        mailer.send(Email(
          "Vos invitations JapanImpact",
          "Comité JapanImpact <comite@japan-impact.ch>",
          Seq(sendTo),
          bodyHtml = Some(views.html.emails.inviteEmail(event).body),
          attachments = attachments
        ))
        Done
      }
  }

}
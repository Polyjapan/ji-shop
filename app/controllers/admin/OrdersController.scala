package controllers.admin

import constants.emails.OrderEmail
import constants.results.Errors._
import data.AuthenticatedUser
import javax.inject.Inject
import models.OrdersModel
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.{AttachmentData, Email, MailerClient}
import play.api.mvc.{AbstractController, Action, ControllerComponents}
import services.TicketGenerator
import utils.Implicits._
import pdi.jwt.JwtSession._

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class OrdersController @Inject()(cc: ControllerComponents, orders: OrdersModel, pdfGen: TicketGenerator)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {
  private val validationRequest = Form(mapping("orderId" -> number, "targetEmail" -> optional(email))(Tuple2.apply)(Tuple2.unapply))

  /**
    * Force the validation of an order (i.e. bypass IPN). The body should be a json containing the orderId, and an
    * optional targetEmail field that, when present, overrides the destination email and replaces the email content by
    * a sweet invitation message
    */
  def validateOrder: Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm("admin.force_validation")) noPermissions.asFuture
    else validationRequest.bindFromRequest.fold(err =>
      formError(err).asFuture, {
      case (orderId, None) =>
        processOrder(orderId, OrderEmail.sendOrderEmail)
      case (orderId, Some(v)) =>
        processOrder(orderId, sendInviteEmail(v))
    })
  }
  }

  def sendInviteEmail(email: String)(attachments: Seq[AttachmentData], client: data.Client)(implicit mailerClient: MailerClient): String =
    mailerClient.send(Email(
      "Vos invitations JapanImpact",
      "Comité JapanImpact <comite@japan-impact.ch>",
      Seq(client.email),
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
      case (Seq(), _) => NotFound.asError("error.order_not_found")
      case (oldSeq, client) if oldSeq.nonEmpty =>
        val attachments: Seq[AttachmentData] =
          oldSeq.map(pdfGen.genPdf).map(p => AttachmentData(p._1, p._2, "application/pdf"))

        mailSender(attachments, client)

        Ok
      case _ => BadRequest.asError("error.already_accepted")
    }
  }


}

package constants.emails

import play.api.libs.mailer.{AttachmentData, Email, MailerClient}

/**
  * @author zyuiop
  */
object OrderEmail {
  def sendOrderEmail(attachments: Seq[AttachmentData], client: data.Client, email: Option[String] = None)(implicit mailerClient: MailerClient): String =
    mailerClient.send(Email(
      "Vos billets JapanImpact",
      "Billetterie JapanImpact <ticket@japan-impact.ch>",
      Seq(email.getOrElse(client.email)),
      bodyText = Some("Bonjour, " +
        "\nVous avez réalisé des achats sur la boutique JapanImpact et nous vous en remercions." +
        "\nVous trouverez en pièce jointe vos billets. Vous pouvez les imprimer ou les présenter sur smartphone." +
        "\nLes billets sont non nominatifs, mais ils ne peuvent être utilisés qu'une seule fois. Si vous avez un tarif réduit, n'oubliez pas de prendre votre justificatif avec vous, ou il pourrait vous être demandé de payer la différence." +
        "\n\nAvec nos remerciements," +
        "\nL'équipe Japan Impact"),
      attachments = attachments
    ))
}

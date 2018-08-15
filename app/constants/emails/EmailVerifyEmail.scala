package constants.emails

import java.net.URLEncoder

import play.api.Configuration
import play.api.libs.mailer.{AttachmentData, Email, MailerClient}

/**
  * @author zyuiop
  */
object EmailVerifyEmail {
  def sendVerifyEmail(email: String, code: String)(implicit mailerClient: MailerClient, config: Configuration): String = {
    val emailEncoded = URLEncoder.encode(email, "UTF-8")

    val url = config.get[String]("polyjapan.siteUrl") + "/emailConfirm/" + emailEncoded + "/" + code

    // Send an email
    mailerClient.send(Email(
      "Votre compte JapanImpact",
      "Ne pas répondre <noreply@japan-impact.ch>",
      Seq(email),
      bodyText = Some("Bienvenue sur la boutique Japan Impact " +
        "\nPour confirmer que cette addresse e-mail est correcte, merci de bien vouloir cliquer sur le lien ci dessous. Vous pourrez ensuite vous connecter à la boutique " +
        "\n\n" + url +
        "\n\nCordialement, " +
        "\nLe Comité PolyJapan")
    ))
  }
}

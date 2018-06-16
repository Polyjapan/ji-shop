package controllers.users

import java.net.URLEncoder
import java.security.SecureRandom
import java.sql.Timestamp

import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.libs.mailer._
import play.api.mvc._
import utils.Formats._
import utils.HashHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import constants.results.Errors._
import utils.Implicits._
/**
  * @author zyuiop
  */
class RegisterController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, mailerClient: MailerClient, config: Configuration)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {
  private val registerForm = Form(mapping("email" -> email, "password" -> nonEmptyText(8), "lastname" -> nonEmptyText, "firstname" -> nonEmptyText)(Tuple4.apply)(Tuple4.unapply))
  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val random = new Random(new SecureRandom())


  def postSignup = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, userData => {
        // If we have no error in the form itself we try to find the user data
        clients.findClient(userData._1).map { opt =>
          if (opt.isDefined) {
            BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.user_exists"))))
          } else {
            val hashed = hash.hash(userData._2)
            val emailCode = List.fill(30)(random.nextInt(chars.length)).map(chars).mkString
            val emailEncoded = URLEncoder.encode(userData._1, "UTF-8")

            clients.createClient(data.Client(Option.empty, userData._3, userData._4, userData._1, Some(emailCode), hashed._2, hashed._1))

            val url = config.get[String]("polyjapan.siteUrl") + "/emailConfirm#mail=" + emailEncoded + "&code=" + emailCode

            // Send an email
            mailerClient.send(Email(
              request.messages("users.signup.email_title"),
              request.messages("users.signup.email_from") + " <noreply@japan-impact.ch>",
              Seq(userData._1),
              bodyText = Some(request.messages("users.signup.email_text", url))
            ))

            Ok(Json.obj("success" -> true, "errors" -> JsArray()))

          }
        }
      }
    )
  }
  }
}

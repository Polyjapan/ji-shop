package controllers.users

import java.net.URLEncoder
import java.security.SecureRandom
import java.sql.Timestamp

import constants.ErrorCodes
import constants.emails.EmailVerifyEmail
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
import utils.{HashHelper, RandomUtils}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import constants.results.Errors._
import services.ReCaptchaClient
import utils.Implicits._
/**
  * @author zyuiop
  */
class RegisterController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, captchaService: ReCaptchaClient)(implicit ec: ExecutionContext, mailerClient: MailerClient, config: Configuration) extends MessagesAbstractController(cc) with I18nSupport {
  private val registerForm = Form(mapping("email" -> email, "password" -> nonEmptyText(8), "lastname" -> nonEmptyText, "firstname" -> nonEmptyText, "captcha" -> nonEmptyText)(Tuple5.apply)(Tuple5.unapply))



  def postSignup = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, userData => {
        // Check the captcha
        captchaService.checkCaptchaWithExpiration(userData._5).flatMap(result =>
          if (!result.success) BadRequest.asError(ErrorCodes.CAPTCHA).asFuture

          // If we have no error in the form itself we try to find the user data
          else clients.findClient(userData._1).map { opt =>
            if (opt.isDefined) {
              // User already exists: don't create it
              EmailVerifyEmail.sendAccountExistsEmail(userData._1)
            } else {
              val hashed = hash.hash(userData._2)
              val emailCode = RandomUtils.randomString(30)

              clients.createClient(data.Client(Option.empty, userData._3, userData._4, userData._1, Some(emailCode), hashed._2, hashed._1))


              EmailVerifyEmail.sendVerifyEmail(userData._1, emailCode)
            }

            Ok(Json.obj("success" -> true, "errors" -> JsArray()))
          }
        )
      }
    )
  }
  }
}

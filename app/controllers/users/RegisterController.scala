package controllers.users

import constants.ErrorCodes
import constants.emails.EmailVerifyEmail
import constants.results.Errors._
import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.libs.mailer._
import play.api.mvc._
import services.ReCaptchaClient
import utils.Implicits._
import utils.{HashHelper, RandomUtils}

import scala.concurrent.ExecutionContext

/**
  * @author zyuiop
  */
class RegisterController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, captchaService: ReCaptchaClient)(implicit ec: ExecutionContext, mailerClient: MailerClient, config: Configuration) extends MessagesAbstractController(cc) with I18nSupport {
  private val registerForm = Form(mapping("email" -> email, "password" -> nonEmptyText(8), "lastname" -> nonEmptyText, "firstname" -> nonEmptyText, "captcha" -> nonEmptyText, "acceptNews" -> default(boolean, false))(RegisterForm.apply)(RegisterForm.unapply))

  case class RegisterForm(email: String, password: String, lastName: String, firstName: String, captcha: String, acceptNews: Boolean)

  def postSignup = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      formError(_).asFuture, formData => {
        // Check the captcha
        captchaService.checkCaptchaWithExpiration(formData.captcha).flatMap(result =>
          if (!result.success) BadRequest.asError(ErrorCodes.CAPTCHA).asFuture

          // If we have no error in the form itself we try to find the user data
          else clients.findClient(formData.email).map { opt =>
            if (opt.isDefined) {
              // User already exists: don't create it
              EmailVerifyEmail.sendAccountExistsEmail(formData.email)
            } else {
              val hashed = hash.hash(formData.password)
              val emailCode = RandomUtils.randomString(30)

              clients.createClient(data.Client(Option.empty, formData.lastName, formData.firstName, formData.email, Some(emailCode), hashed._2, hashed._1, acceptNewsletter = formData.acceptNews))


              EmailVerifyEmail.sendVerifyEmail(formData.email, emailCode)
            }

            Ok(Json.obj("success" -> true, "errors" -> JsArray()))
          }
        )
      }
    )
  }
  }
}

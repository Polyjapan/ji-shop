package controllers

import java.net.{URLDecoder, URLEncoder}

import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.data.validation.ValidationError
import play.api.i18n.{I18nSupport, Lang, Messages}
import play.api.libs.mailer._
import play.api.mvc._
import utils.HashHelper
import views.html.helper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * @author zyuiop
  */
class UsersController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, mailerClient: MailerClient, config: Configuration)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {
  private val loginForm = Form(mapping("email" -> email, "password" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))
  private val registerForm = Form(mapping("email" -> email, "password" -> nonEmptyText, "password_repeat" -> nonEmptyText, "lastname" -> nonEmptyText, "firstname" -> nonEmptyText)(Tuple5.apply)(Tuple5.unapply))

  def login = Action { implicit request =>
    Ok(views.html.Users.login(loginForm))
  }

  def postLogin: Action[AnyContent] = Action.async { implicit request => {
    loginForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => {
        // If we have errors, we show the form again with the errors
        Future(BadRequest(views.html.Users.login(withErrors)))
      }, userData => {
        // If we have no error in the form itself we try to find the user data
        clients.findClient(userData._1).map { opt =>
          if (opt.isDefined) {
            // We have found a client: check the password
            val (client, perms) = opt.get

            if (hash.check(client.passwordAlgo, client.password, userData._2)) {
              if (client.emailConfirmKey.nonEmpty)
                BadRequest(views.html.Users.login(loginForm.withGlobalError(request.messages("users.login.email_not_confirmed"))))
              else
                Ok(client.toString) // TODO : session and redirect
            } else BadRequest(views.html.Users.login(loginForm.withGlobalError(request.messages("users.login.error_not_found"))))
          } else BadRequest(views.html.Users.login(loginForm.withGlobalError(request.messages("users.login.error_not_found"))))
        }
      }
    )
  } }

  /**
    * Disconnects the user: redirect him to the home cleaning its session
    */
  def logout = Action { implicit request =>
    Redirect("/").withNewSession.flashing("message" -> request.messages.apply("users.logout.done"))
  }

  def signup = Action { implicit request =>
    Ok(views.html.Users.register(registerForm))
  }

  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')

  def postSignup: Action[AnyContent] = Action.async { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      withErrors => {
        // If we have errors, we show the form again with the errors
        Future(BadRequest(views.html.Users.register(withErrors)))
      }, userData => {
        // If we have no error in the form itself we try to find the user data
        clients.findClient(userData._1).map { opt =>
          if (opt.isDefined) {
            BadRequest(views.html.Users.register(form.withGlobalError(request.messages("users.signup.email_used"))))
          } else {
            // Okay, check password length:
            if (userData._2.length < 8) {
              BadRequest(views.html.Users.register(form.withError("password", request.messages("users.signup.password_not_strong"))))
            } else if (userData._2 != userData._3) {
              BadRequest(views.html.Users.register(form.withError("password_repeat", request.messages("users.signup.password_repeat_incorrect"))))
            } else {
              val hashed = hash.hash(userData._2)
              val emailCode = List.fill(30)(Random.nextInt(chars.length)).map(chars).mkString

              val emailEncoded = URLEncoder.encode(userData._1, "UTF-8")

              clients.createClient(data.Client(Option.empty, userData._4, userData._5, userData._1, Some(emailCode), hashed._2, hashed._1))

              val url = config.get[String]("polyjapan.siteUrl") + routes.UsersController.emailConfirm(emailEncoded, emailCode)

              // Send an email
              mailerClient.send(Email(
                request.messages("users.signup.email_title"),
                request.messages("users.signup.email_from") + " <noreply@japan-impact.ch>",
                Seq(userData._1),
                bodyText = Some("WESH " + url)
              ))

              Ok
            }
          }
        }
      }
    )
  } }

  def passwordReset = Action {
    Ok
  }

  def passwordChange = Action {
    Ok
  }

  def emailConfirm(email: String, code: String): Action[AnyContent] = Action.async { implicit request => {
    val emailDecoded = URLDecoder.decode(email, "UTF-8")

    clients.findClient(emailDecoded).map { opt =>
      if (opt.isEmpty)
        NotFound // TODO : print some stuff
      else {
        val client = opt.get._1
        if (client.emailConfirmKey.isEmpty) {
          BadRequest // TODO : print some stuff
        } else {
          clients.updateClient(client.copy(emailConfirmKey = None)) // TODO : print some stuff
          Ok
        }
      }
    }
    }
  }
}

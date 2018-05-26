package controllers

import java.net.{URLDecoder, URLEncoder}
import java.sql.Timestamp

import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.mailer._
import play.api.mvc._
import utils.HashHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

/**
  * @author zyuiop
  */
class UsersController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, mailerClient: MailerClient, config: Configuration)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {
  private val loginForm = Form(mapping("email" -> email, "password" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))
  private val registerForm = Form(mapping("email" -> email, "password" -> nonEmptyText(8), "password_repeat" -> nonEmptyText, "lastname" -> nonEmptyText, "firstname" -> nonEmptyText)(Tuple5.apply)(Tuple5.unapply))
  private val recoverForm = Form(mapping("email" -> email)(e => e)(Some(_)))
  private val resetForm = Form(mapping("email" -> email, "code" -> nonEmptyText, "password" -> nonEmptyText(8), "password_repeat" -> nonEmptyText)(Tuple4.apply)(Tuple4.unapply))

  // Todo: block access to these pages to logged in users

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

            // TODO: upgrade security if algo is not the current default one
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
  }
  }

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
            // Okay, check password data:
            if (userData._2 != userData._3) {
              BadRequest(views.html.Users.register(form.withError("password_repeat", "users.signup.password_repeat_incorrect")))
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
                bodyText = Some(request.messages("users.signup.email_text", url))
              ))

              Ok
            }
          }
        }
      }
    )
  }
  }

  def recoverPassword = Action { implicit request =>
    Ok(views.html.Users.recoverPassword(recoverForm))
  }

  def recoverPasswordSend = Action { implicit request =>
    recoverForm.bindFromRequest.fold(
      withErrors => BadRequest(views.html.Users.recoverPassword(withErrors)), email => {
        clients.findClient(email).map {
          case Some((client, perms)) =>
            val resetCode = List.fill(30)(Random.nextInt(chars.length)).map(chars).mkString
            val emailEncoded = URLEncoder.encode(client.email, "UTF-8")

            val url = config.get[String]("polyjapan.siteUrl") + routes.UsersController.passwordReset(emailEncoded, resetCode)

            // TODO captcha ?

            clients.updateClient(client.copy(
              passwordReset = Some(resetCode),
              passwordResetEnd = Some(
                new Timestamp(System.currentTimeMillis + (24 * 3600 * 1000))
              ))).onComplete(_ => {
              mailerClient.send(Email(
                request.messages("users.recover.email_title"),
                request.messages("users.recover.email_from") + " <noreply@japan-impact.ch>",
                Seq(client.email),
                bodyText = Some(request.messages("users.recover.email_text", client.firstname, url))
              ))
            })

          case None => mailerClient.send(Email(
            request.messages("users.recover.email_title"),
            request.messages("users.recover.email_from") + " <noreply@japan-impact.ch>",
            Seq(email),
            bodyText = Some(request.messages("users.recover.no_user_email_text"))
          ))
        }
      }
    )
    Ok(views.html.Users.recoverPassword(recoverForm))
  }

  private def checkPasswordRequest(client: data.Client, code: String): Boolean =
    client.passwordReset.contains(code) && client.passwordResetEnd.exists(_.getTime > System.currentTimeMillis)

  def passwordReset(email: String, code: String) = Action.async { implicit request =>
    val emailDecoded = URLDecoder.decode(email, "UTF-8")

    clients.findClient(emailDecoded).map { opt =>
      if (opt.isEmpty)
        NotFound // TODO : print some stuff
      else {
        val client = opt.get._1
        if (!checkPasswordRequest(client, code)) {
          NotFound // TODO : print same stuff
        } else {
          Ok(views.html.Users.passwordReset(resetForm.fill((emailDecoded, code, "", ""))))
        }
      }
    }
  }

  def passwordResetSend = Action.async { implicit request =>
    val form = resetForm.bindFromRequest

    form.fold(
      withErrors => Future(BadRequest(views.html.Users.passwordReset(withErrors))),
      { case (email, code, pass, passConfirm) =>
        clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            NotFound // TODO : print some stuff
          else {
            val client = opt.get._1
            if (!checkPasswordRequest(client, code)) {
              NotFound // TODO : print same stuff
            } else {
              if (pass != passConfirm) {
                BadRequest(views.html.Users.passwordReset(form.withError("password_repeat", "users.signup.password_repeat_incorrect")))
              } else {
                val (algo, hashPass) = hash.hash(pass)
                clients.updateClient(client.copy(passwordReset = None, passwordResetEnd = None, password = hashPass, passwordAlgo = algo)) // TODO : print some stuff
                Ok
              }

            }
          }
        }
      })


  }

  def emailConfirm(email: String, code: String): Action[AnyContent] = Action.async { implicit request => {
    val emailDecoded = URLDecoder.decode(email, "UTF-8")

    clients.findClient(emailDecoded).map { opt =>
      if (opt.isEmpty)
        NotFound // TODO : print some stuff
      else {
        val client = opt.get._1

        if (!client.emailConfirmKey.contains(code)) {
          NotFound // TODO : print some stuff (same stuff)
        } else {
          clients.updateClient(client.copy(emailConfirmKey = None)) // TODO : print some stuff
          Ok
        }
      }
    }
  }
  }
}

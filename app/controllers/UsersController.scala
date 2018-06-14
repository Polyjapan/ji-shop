package controllers

import java.net.{URLDecoder, URLEncoder}
import java.security.SecureRandom
import java.sql.Timestamp

import utils.Formats._
import data.AuthenticatedUser
import javax.inject.Inject
import models.ClientsModel
import pdi.jwt.JwtSession._
import pdi.jwt._
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json._
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
  private val registerForm = Form(mapping("email" -> email, "password" -> nonEmptyText(8), "lastname" -> nonEmptyText, "firstname" -> nonEmptyText)(Tuple4.apply)(Tuple4.unapply))
  private val recoverForm = Form(mapping("email" -> email)(e => e)(Some(_)))
  private val resetForm = Form(mapping("email" -> email, "code" -> nonEmptyText, "password" -> nonEmptyText(8))(Tuple3.apply)(Tuple3.unapply))
  private val emailConfirm = Form(mapping("email" -> email, "code" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))



  def postLogin = Action.async(parse.json) { implicit request: Request[JsValue] => {
    loginForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => {

        // If we have errors, we show the form again with the errors
        Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors)))
      }, userData => {
        // If we have no error in the form itself we try to find the user data
        clients.findClient(userData._1).map { opt =>
          if (opt.isDefined) {
            // We have found a client: check the password
            val (client, perms) = opt.get

            if (hash.check(client.passwordAlgo, client.password, userData._2)) {
              if (client.emailConfirmKey.nonEmpty)
                BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.email_not_confirmed"))))
              else {
                // We try to upgrade the password of the user if it's using an insecure algo
                val newPasword = hash.upgrade(client.passwordAlgo, userData._2)

                // If there is a new password, it means the algorithm was insecure, so we have to update the user
                if (newPasword.nonEmpty) {
                  newPasword.get match {
                    case (algo, newHash) =>
                      val nc = client.copy(passwordAlgo = algo, password = newHash)
                      clients.updateClient(nc)
                  }
                }

                // Create the JWT Session
                val session = JwtSession() + ("user", AuthenticatedUser(client, perms))
                Ok(Json.obj("success" -> true, "errors" -> JsArray())).withJwtSession(session)
              }
            } else BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.not_found"))))
          } else BadRequest(Json.obj("success" -> false, "errors" -> Seq(FormError("", "error.not_found"))))

        }
      }
    )
  }
  }

  private val chars = ('a' to 'z') ++ ('A' to 'Z') ++ ('0' to '9')
  private val random = new Random(new SecureRandom())


  def postSignup = Action.async(parse.json) { implicit request => {
    val form = registerForm.bindFromRequest

    form.fold( // We bind the request to the form
      withErrors => {
        // If we have errors, we show the form again with the errors
        Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors)))
      }, userData => {
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

  def recoverPasswordSend = Action(parse.json) { implicit request =>
    recoverForm.bindFromRequest.fold(
      withErrors => Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors))), email => {
        clients.findClient(email).map {
          case Some((client, perms)) =>
            val resetCode = List.fill(30)(random.nextInt(chars.length)).map(chars).mkString
            val emailEncoded = URLEncoder.encode(client.email, "UTF-8")

            val url = config.get[String]("polyjapan.siteUrl") + "/passwordReset#mail=" + emailEncoded + "&code=" + resetCode

            // TODO captcha

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
    Ok
  }

  private def checkPasswordRequest(client: data.Client, code: String): Boolean =
    client.passwordReset.contains(code) && client.passwordResetEnd.exists(_.getTime > System.currentTimeMillis)

  def passwordResetSend = Action.async(parse.json) { implicit request =>
    val form = resetForm.bindFromRequest

    form.fold(
      withErrors => Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors))),
      { case (email, code, pass) =>
        clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("email", "error.not_found"))))
          else {
            val client = opt.get._1
            if (!checkPasswordRequest(client, code)) {
              NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("code", "error.not_found"))))
            } else {
              val (algo, hashPass) = hash.hash(pass)
              clients.updateClient(client.copy(passwordReset = None, passwordResetEnd = None, password = hashPass, passwordAlgo = algo))
              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
            }
          }
        }
      })


  }

  def emailConfirmProcess = Action.async(parse.json) { implicit request => {
    val form = emailConfirm.bindFromRequest

    form.fold(
      withErrors => Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors))),
      { case (email, code) =>
        clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("email", "error.not_found"))))
          else {
            val client = opt.get._1
            if (!client.emailConfirmKey.contains(code)) {
              NotFound(Json.obj("success" -> false, "errors" -> Seq(FormError("code", "error.not_found"))))
            } else {
              clients.updateClient(client.copy(emailConfirmKey = None))
              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
            }
          }
        }
      })
  }
  }
}

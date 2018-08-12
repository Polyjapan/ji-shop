package controllers.users

import java.net.URLEncoder
import java.security.SecureRandom
import java.sql.Timestamp

import javax.inject.Inject
import models.{ClientsModel, JsonOrderData}
import play.api.Configuration
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.I18nSupport
import play.api.libs.json._
import play.api.libs.mailer._
import play.api.mvc._
import utils.Formats._
import utils.{HashHelper, RandomUtils}
import pdi.jwt.JwtSession._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random
import constants.results.Errors._
import data.AuthenticatedUser
import utils.Implicits._
/**
  * @author zyuiop
  */
class PasswordResetController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, mailerClient: MailerClient, config: Configuration)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {
  private val recoverForm = Form(mapping("email" -> email)(e => e)(Some(_)))
  private val resetForm = Form(mapping("email" -> email, "code" -> nonEmptyText, "password" -> nonEmptyText(8))(Tuple3.apply)(Tuple3.unapply))
  private val changeForm = Form(mapping("password" -> nonEmptyText(8))(e => e)(Some(_)))


  def recoverPasswordSend: Action[JsValue] = Action(parse.json) { implicit request =>
    recoverForm.bindFromRequest.fold(
      withErrors => Future(BadRequest(Json.obj("success" -> false, "errors" -> withErrors.errors))), email => {
        clients.findClient(email).map {
          case Some((client, perms)) =>
            val resetCode = RandomUtils.randomString(30)
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
    Ok(Json.obj("success" -> true, "errors" -> JsArray()))
  }

  private def checkPasswordRequest(client: data.Client, code: String): Boolean =
    client.passwordReset.contains(code) && client.passwordResetEnd.exists(_.getTime > System.currentTimeMillis)

  def passwordResetSend: Action[JsValue] = Action.async(parse.json) { implicit request =>
    val form = resetForm.bindFromRequest

    form.fold(
      withErrors => formError(withErrors).asFuture,
      { case (email, code, pass) =>
        clients.findClient(email).map { opt =>
          if (opt.isEmpty)
            notFound("email")
          else {
            val client = opt.get._1
            if (!checkPasswordRequest(client, code)) {
              notFound("code")
            } else {
              val (algo, hashPass) = hash.hash(pass)
              clients.updateClient(client.copy(passwordReset = None, passwordResetEnd = None, password = hashPass, passwordAlgo = algo))
              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
            }
          }
        }
      })


  }

  def passwordChange: Action[JsValue] = Action.async(parse.json) { implicit request =>
    val form = changeForm.bindFromRequest
    val session = request.jwtSession
    val user = session.getAs[AuthenticatedUser]("user")

    if (user.isEmpty)
      notAuthenticated.asFuture
    else
      form.fold(
        withErrors => formError(withErrors).asFuture,
        pass =>
          clients.findClient(user.get.email).map { opt =>
            if (opt.isEmpty)
              notFound("email")
            else {
              val client = opt.get._1
              val (algo, hashPass) = hash.hash(pass)
              clients.updateClient(client.copy(passwordReset = None, passwordResetEnd = None, password = hashPass, passwordAlgo = algo))
              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
            }
          }
        )
  }
}

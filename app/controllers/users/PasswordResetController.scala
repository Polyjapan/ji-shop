package controllers.users

import java.net.URLEncoder
import java.sql.Timestamp

import constants.ErrorCodes
import constants.results.Errors._
import data.AuthenticatedUser
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
import pdi.jwt._
import pdi.jwt.JwtSession._

import scala.concurrent.ExecutionContext
/**
  * @author zyuiop
  */
class PasswordResetController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, mailerClient: MailerClient, captchaClient: ReCaptchaClient)(implicit ec: ExecutionContext, config: Configuration) extends MessagesAbstractController(cc) with I18nSupport {
  private val recoverForm = Form(mapping("email" -> email, "captcha" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))
  private val resetForm = Form(mapping("email" -> email, "code" -> nonEmptyText, "password" -> nonEmptyText(8))(Tuple3.apply)(Tuple3.unapply))
  private val changeForm = Form(mapping("password" -> nonEmptyText(8))(e => e)(Some(_)))


  def recoverPasswordSend: Action[JsValue] = Action.async(parse.json) { implicit request =>
    recoverForm.bindFromRequest.fold(
      withErrors => BadRequest.asFormErrorSeq(withErrors.errors).asFuture,
      { case (email, captcha) =>
        captchaClient.checkCaptchaWithExpiration(captcha).flatMap(captchaResponse => {
          // We consider that a captcha that was validated more than 6hrs ago is too old
          if (!captchaResponse.success) BadRequest.asError(ErrorCodes.CAPTCHA).asFuture
          else clients.findClient(email).map {
            case Some((client, perms)) =>
              val resetCode = RandomUtils.randomString(30)
              val emailEncoded = URLEncoder.encode(client.email, "UTF-8")

              val url = config.get[String]("polyjapan.siteUrl") + "/passwordReset/" + emailEncoded + "/" + resetCode

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


              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
            case None => mailerClient.send(Email(
              request.messages("users.recover.email_title"),
              request.messages("users.recover.email_from") + " <noreply@japan-impact.ch>",
              Seq(email),
              bodyText = Some(request.messages("users.recover.no_user_email_text"))
            ))

              Ok(Json.obj("success" -> true, "errors" -> JsArray()))
          }
        })


      }
    )
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
            notFound()
          else {
            val client = opt.get._1
            if (!checkPasswordRequest(client, code)) {
              notFound()
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

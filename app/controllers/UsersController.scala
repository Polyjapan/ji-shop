package controllers

import javax.inject.Inject

import models.ClientsModel
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{AbstractController, ControllerComponents}

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class UsersController @Inject()(cc: ControllerComponents, clients: ClientsModel)(implicit ec: ExecutionContext) extends AbstractController(cc) with I18nSupport {
  private val loginForm = Form(mapping("email" -> email, "password" -> nonEmptyText)(Tuple2.apply)(Tuple2.unapply))

  def login = Action { implicit request =>
    Ok(views.html.Users.login(loginForm))
  }

  def postLogin = Action.async { implicit request => {
    loginForm.bindFromRequest.fold(
      withErrors => {
        Future(BadRequest(views.html.Users.login(withErrors)))
      }, userData => {
        // Do what we must do
        Future(Ok(userData.toString()))
      }
    )
  } }

  /**
    * Disconnects the user: redirect him to the home cleaning its session
    */
  def logout = Action { implicit request =>
    Redirect("/").withNewSession.flashing("message" -> request.messages.apply("users.logout.done"))
  }

  def signup = Action {
    Ok
  }

  def passwordReset = Action {
    Ok
  }

  def passwordChange = Action {
    Ok
  }

  def emailConfirm = Action {
    Ok
  }
}

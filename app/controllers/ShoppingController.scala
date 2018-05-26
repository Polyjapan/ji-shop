package controllers

import javax.inject.Inject
import models.ClientsModel
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import utils.HashHelper

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ShoppingController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, hash: HashHelper, mailerClient: MailerClient, config: Configuration)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {

  def homepage: Action[AnyContent] = Action.async { implicit request => {
    Future(Ok)
  }
  }

}

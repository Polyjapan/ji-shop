package controllers.admin

import constants.Permissions
import constants.results.Errors._
import data.{AuthenticatedUser, Event, Source}
import javax.inject.Inject
import models.{EventsModel, OrdersModel, ProductsModel, SalesData, StatsModel}
import pdi.jwt.JwtSession._
import play.api.data.Form
import play.api.data.Forms.mapping
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._
import play.api.data.Forms._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class EventsController @Inject()(cc: ControllerComponents, events: EventsModel, products: ProductsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {

  def getEvents: Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      events.getEvents.map(e => Ok(Json.toJson(e)))
    }
  }
  }

  def getEvent(id: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      events.getEvent(id).map(e => Ok(Json.toJson(e)))
    }
  }
  }

  val form = Form(mapping("id" -> optional(number), "name" -> nonEmptyText, "location" -> nonEmptyText, "visible" -> boolean)(Event.apply)(Event.unapply))

  private def createOrUpdateEvent(handler: Event => Future[Result]): Action[JsValue] = Action.async(parse.json) { implicit request => {

    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.ADMIN_ACCESS)) noPermissions.asFuture
    else {
      form.bindFromRequest.fold( // We bind the request to the form
        withErrors => {
          // If we have errors, we show the form again with the errors
          formError(withErrors).asFuture
        }, data => {
          handler(data)
        })
    }
  }
  }

  def createEvent: Action[JsValue] = createOrUpdateEvent(
    ev => events.createEvent(ev.copy(Option.empty)).map(id => Ok(Json.toJson(id))))

  def cloneEvent(id: Int): Action[JsValue] = createOrUpdateEvent(
    ev => events.createEvent(ev.copy(Option.empty))
      .flatMap(newEvent => {
        products.cloneProducts(id, newEvent)
          .map(_ => newEvent) // ignore result and just return the new event id

      }).map(id => Ok(Json.toJson(id))))

  def updateEvent(id: Int): Action[JsValue] = createOrUpdateEvent(
    ev => events.updateEvent(id, ev.copy(Some(id))).map(_ => Ok(Json.toJson(id))))
}

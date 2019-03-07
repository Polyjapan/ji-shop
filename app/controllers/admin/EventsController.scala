package controllers.admin

import com.mysql.jdbc.exceptions.MySQLIntegrityConstraintViolationException
import constants.ErrorCodes
import constants.Permissions._
import constants.results.Errors
import constants.results.Errors._
import data.Event
import exceptions.HasItemsException
import javax.inject.Inject
import models.{EventsModel, ProductsModel}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

/**
  * @author zyuiop
  */
class EventsController @Inject()(cc: ControllerComponents, events: EventsModel, products: ProductsModel)(implicit mailerClient: MailerClient, ec: ExecutionContext, config: Configuration) extends AbstractController(cc) {

  def getEvents: Action[AnyContent] = Action.async {
    events.getEvents.map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

  def getEvent(id: Int): Action[AnyContent] = Action.async {
    events.getEvent(id).map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS

  val form = Form(mapping("id" -> optional(number), "name" -> nonEmptyText, "location" -> nonEmptyText, "visible" -> boolean, "archived" -> boolean)(Event.apply)(Event.unapply))

  private def createOrUpdateEvent(handler: Event => Future[Result]): Action[JsValue] = Action.async(parse.json) { implicit request => {
    form.bindFromRequest.fold( // We bind the request to the form
      withErrors => {
        // If we have errors, we show the form again with the errors
        formError(withErrors).asFuture
      }, data => {
        handler(data)
      })
  }
  } requiresPermission ADMIN_EVENT_MANAGE

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

  /**
    * Delete an event. One can delete an event only if it has no product.
    * @param id the id of the event to delete
    */
  def deleteEvent(id: Int): Action[AnyContent] = Action.async {
    events.deleteEvent(id).map(r =>
      if (r == 1) Ok.asSuccess
      else notFound("event")
    ).recover {
      case HasItemsException() => BadRequest.asError(ErrorCodes.NOT_EMPTY_EVENT)
    }
  } requiresPermission ADMIN_EVENT_MANAGE
}

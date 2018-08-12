package controllers

import akka.actor.ActorSystem
import javax.inject._
import play.api.mvc._

import scala.concurrent.ExecutionContext

/**
  * This controller replies to all OPTIONS requests
  */
@Singleton
class OptionsController @Inject()(cc: ControllerComponents, actorSystem: ActorSystem)(implicit exec: ExecutionContext) extends AbstractController(cc) {
  def headers = List(
    "Access-Control-Allow-Origin" -> "*", // TODO: replace
    "Access-Control-Allow-Methods" -> "GET, POST, OPTIONS, DELETE, PUT",
    "Access-Control-Max-Age" -> "3600",
    "Access-Control-Allow-Headers" -> "Origin, Content-Type, Accept, Authorization",
    "Access-Control-Allow-Credentials" -> "true"
  )
  def rootOptions = options("/")

  def options(url: String) = Action { request =>
    NoContent.withHeaders(headers : _*)
  }
}

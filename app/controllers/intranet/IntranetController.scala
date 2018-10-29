package controllers.intranet

import constants.Permissions
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.IntranetModel
import pdi.jwt.JwtSession._
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class IntranetController @Inject()(cc: ControllerComponents, model: IntranetModel)(implicit mailerClient: MailerClient, ec: ExecutionContext) extends AbstractController(cc) {

  val taskForm = Form(mapping(
    "name" -> nonEmptyText(minLength = 1, maxLength = 140),
    "initialComment" -> nonEmptyText,
    "tags" -> text,
    "priority" -> number)(Tuple4.apply)(Tuple4.unapply))

  private def parseTags(tags: String) =
    tags.split(";").map(_.trim)
      .map(_.replaceAll("[^a-zA-Z0-9_-]", "")) // tags = alphanumeric characters
      .map(tag => if (tag.length > 140) tag.substring(0, 140) else tag)


  /**
    * Creates a task and returns its id
    */
  def createTask(event: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_TASK_POST)) noPermissions.asFuture
    else {
      this.taskForm.bindFromRequest.fold( // We bind the request to the form
        withErrors => formError(withErrors).asFuture,
        taskData => {
          val state: TaskState =
            if (user.get.hasPerm(Permissions.INTRANET_TASK_ACCEPT)) Waiting
            else Sent
          val task = IntranetTask(None, taskData._1, taskData._4, state, user.get.id, null, event)
          val firstComm = IntranetTaskComment(None, 0, taskData._2, user.get.id, None)
          val tags = parseTags(taskData._3)

          model.createTask(task, firstComm, tags).map(res => Ok(Json.toJson(res)))
        })
    }
  }
  }


  val priorityForm = Form(mapping(
    "priority" -> number)(e => e)(Some(_)))

  /**
    * Updates a task priority
    */
  def updatePriority(task: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_TASK_EDIT)) noPermissions.asFuture
    else {
      this.priorityForm.bindFromRequest.fold( // We bind the request to the form
        withErrors => formError(withErrors).asFuture,
        taskData => {
          model.updatePriority(task, taskData).map(res => if (res > 0) Ok.asSuccess else dbError)
        })
    }
  }
  }

  val stateForm = Form(mapping(
    "state" -> of[TaskState](TaskState.formatter))(e => e)(Some(_)))

  /**
    * Updates a task state
    */
  def updateState(task: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_TASK_CHANGE_STATE)) noPermissions.asFuture
    else {
      this.stateForm.bindFromRequest.fold( // We bind the request to the form
        withErrors => formError(withErrors).asFuture,
        taskData => {
          val state = IntranetTaskLog(None, task, taskData, user.get.id, None)
          model.changeState(state).map(res => if (res > 0) Ok.asSuccess else dbError)
        })
    }
  }
  }

  /**
    * Get all the tasks for an event
    */
  def getTasks(event: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_VIEW)) noPermissions.asFuture
    else {
      model.getTasksByEvent(event).map(e => Ok(Json.toJson(e)))
    }
  }
  }

  /**
    * Get all the tasks for an event and tags
    */
  def getTasksWithTags(event: Int, tags: Seq[String]): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_VIEW)) noPermissions.asFuture
    else {
      model.getTasksByEventWithTags(event, tags).map(e => Ok(Json.toJson(e)))
    }
  }
  }

  /**
    * Get a single task by its id
    */
  def getTask(task: Int): Action[AnyContent] = Action.async { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_VIEW)) noPermissions.asFuture
    else {
      model.getTask(task).map(e => if (e.isDefined) Ok(Json.toJson(e.get)) else notFound("task"))
    }
  }
  }

  /**
    * Creates a comment
    */
  def addComment(task: Int): Action[String] = Action.async(parse.text) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_VIEW)) noPermissions.asFuture
    else if (!request.hasBody) BadRequest.asError().asFuture
    else {
      val text = request.body
      val comment = IntranetTaskComment(None, task, text, user.get.id, None)

      model.postComment(comment).map(res => if (res > 0) Ok.asSuccess else dbError)
    }
  }
  }

  def addOrRemoveTags(dbAction: Seq[String] => Future[Result]): Action[String] = Action.async(parse.text) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_TASK_EDIT)) noPermissions.asFuture
    else if (!request.hasBody) BadRequest.asError().asFuture
    else {
      val tags = parseTags(request.body)

      dbAction(tags)
    }
  }
  }

  /**
    * Add one or multiple tags
    */
  def addTags(task: Int): Action[String] = addOrRemoveTags(tags =>
    model.addTags(task, tags).map(res => if (res.getOrElse(0) > 0) Ok.asSuccess else dbError))

  /**
    * Remove one or multiple tags
    */
  def removeTags(task: Int): Action[String] = addOrRemoveTags(tags =>
    model.removeTags(task, tags).map(res => if (res > 0) Ok.asSuccess else dbError))


  val assignForm = Form(mapping(
    "assignee" -> number)(e => e)(Some(_)))

  private def addOrRemoveAssignee(task: Int, remove: Boolean): Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.jwtSession.getAs[AuthenticatedUser]("user")
    if (user.isEmpty) notAuthenticated.asFuture
    else if (!user.get.hasPerm(Permissions.INTRANET_TASK_TAKE) && !user.get.hasPerm(Permissions.INTRANET_TASK_GIVE) && !user.get.hasPerm(Permissions.INTRANET_TASK_LEAVE)) noPermissions.asFuture
    else {
      this.assignForm.bindFromRequest.fold( // We bind the request to the form
        withErrors => formError(withErrors).asFuture,
        taskData => {
          val perm = if (taskData == user.get.id) {
            if (remove) Permissions.INTRANET_TASK_LEAVE
            else Permissions.INTRANET_TASK_TAKE
          } else Permissions.INTRANET_TASK_GIVE

          if (!user.get.hasPerm(perm))
            noPermissions.asFuture
          else {
            val log = IntranetTaskAssignationLog(None, task, taskData, remove, user.get.id, None)

            model.addOrRemoveAssignee(log).map(res => if (res > 0) Ok.asSuccess else dbError)
          }
        })
    }
  }
  }

  def addAssignee(task: Int) = addOrRemoveAssignee(task, remove = false)

  def removeAssignee(task: Int) = addOrRemoveAssignee(task, remove = true)

}

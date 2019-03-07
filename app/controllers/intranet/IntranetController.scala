package controllers.intranet

import constants.Permissions._
import constants.results.Errors._
import data._
import javax.inject.Inject
import models.IntranetModel
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms.{mapping, _}
import play.api.libs.json.{JsValue, Json}
import play.api.libs.mailer.MailerClient
import play.api.mvc._
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class IntranetController @Inject()(cc: ControllerComponents, model: IntranetModel)(implicit mailerClient: MailerClient, ec: ExecutionContext, config: Configuration) extends AbstractController(cc) {

  val taskForm = Form(mapping(
    "name" -> nonEmptyText(minLength = 1, maxLength = 140),
    "initialComment" -> nonEmptyText,
    "tags" -> text,
    "priority" -> number)(Tuple4.apply)(Tuple4.unapply))

  private def parseTags(tags: String) =
    tags.split(";").map(_.trim)
      .map(_.replaceAll("[^a-zA-Z0-9_-]", "")) // tags = alphanumeric characters
      .map(tag => if (tag.length > 140) tag.substring(0, 140) else tag)
      .filter(_.length > 0)


  /**
    * Creates a task and returns its id
    */
  def createTask(event: Int): Action[JsValue] = Action.async(parse.json) { implicit request => {
    val user = request.user

    this.taskForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => formError(withErrors).asFuture,
      taskData => {
        val state: TaskState =
          if (user.hasPerm(INTRANET_TASK_ACCEPT)) Waiting
          else Sent
        val task = IntranetTask(None, taskData._1, taskData._4, state, user.id, null, event)
        val firstComm = IntranetTaskComment(None, 0, taskData._2, user.id, None)
        val tags = parseTags(taskData._3)

        model.createTask(task, firstComm, tags).map(res => Ok(Json.toJson(res)))
      })
  }
  } requiresPermission INTRANET_TASK_POST


  val priorityForm = Form(mapping(
    "priority" -> number)(e => e)(Some(_)))

  /**
    * Updates a task priority
    */
  def updatePriority(task: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    this.priorityForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => formError(withErrors).asFuture,
      taskData => {
        model.updatePriority(task, taskData).map(res => if (res > 0) Ok.asSuccess else dbError)
      })
  } requiresPermission INTRANET_TASK_EDIT

  val stateForm = Form(mapping(
    "state" -> of[TaskState](TaskState.formatter))(e => e)(Some(_)))

  /**
    * Updates a task state
    */
  def updateState(task: Int): Action[JsValue] = Action.async(parse.json) { implicit request =>
    this.stateForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => formError(withErrors).asFuture,
      taskData => {
        val state = IntranetTaskLog(None, task, taskData, request.user.id, None)
        model.changeState(state).map(res => if (res > 0) Ok.asSuccess else dbError)
      })
  } requiresPermission INTRANET_TASK_CHANGE_STATE

  /**
    * Get all the tasks for an event
    */
  def getTasks(event: Int): Action[AnyContent] = Action.async {
    model.getTasksByEvent(event).map(e => Ok(Json.toJson(e)))
  } requiresPermission INTRANET_VIEW

  /**
    * Get all the tasks for an event and tags
    */
  def getTasksWithTags(event: Int, tags: Seq[String]): Action[AnyContent] = Action.async {
    model.getTasksByEventWithTags(event, tags).map(e => Ok(Json.toJson(e)))
  } requiresPermission INTRANET_VIEW

  /**
    * Get a single task by its id
    */
  def getTask(task: Int): Action[AnyContent] = Action.async {
    model.getTask(task).map(e => if (e.isDefined) Ok(Json.toJson(e.get)) else notFound("task"))
  } requiresPermission INTRANET_VIEW

  /**
    * Creates a comment
    */
  def addComment(task: Int): Action[String] = Action.async(parse.text) { implicit request => {
    if (!request.hasBody) BadRequest.asError().asFuture
    else {
      val text = request.body
      val comment = IntranetTaskComment(None, task, text, request.user.id, None)

      model.postComment(comment).map(res => if (res > 0) Ok.asSuccess else dbError)
    }
  }
  } requiresPermission INTRANET_VIEW

  def addOrRemoveTags(dbAction: Seq[String] => Future[Result]): Action[String] = Action.async(parse.text) { implicit request => {
    if (!request.hasBody) BadRequest.asError().asFuture
    else {
      val tags = parseTags(request.body)

      dbAction(tags)
    }
  }
  } requiresPermission INTRANET_TASK_EDIT

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
    this.assignForm.bindFromRequest.fold( // We bind the request to the form
      withErrors => formError(withErrors).asFuture,
      taskData => {
        val perm =
          if (taskData == request.user.id) {
            if (remove)
              INTRANET_TASK_LEAVE
            else
              INTRANET_TASK_TAKE
          } else
            INTRANET_TASK_GIVE

        if (!request.user.hasPerm(perm))
          noPermissions.asFuture
        else {
          val log = IntranetTaskAssignationLog(None, task, taskData, remove, request.user.id, None)

          model.addOrRemoveAssignee(log).map(res => if (res > 0) Ok.asSuccess else dbError)
        }
      })
  }
  } requiresAuthorizationCheck AuthorizationHandler.ensuringAuthentication.andAlso(user => AuthorizationResult(user.get.hasPerm(INTRANET_TASK_GIVE) || user.get.hasPerm(INTRANET_TASK_TAKE) || user.get.hasPerm(INTRANET_TASK_LEAVE), Some(noPermissions)))

  def addAssignee(task: Int) = addOrRemoveAssignee(task, remove = false)

  def removeAssignee(task: Int) = addOrRemoveAssignee(task, remove = true)

}

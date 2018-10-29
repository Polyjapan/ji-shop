package data

import java.sql.Timestamp

import models.JsonClient
import play.api.libs.json.Json


/**
  * @author Louis Vialar
  */
case class PartialIntranetTask(name: String,
                               priority: Int,
                               state: TaskState,
                               createdBy: JsonClient,
                               createdAt: Timestamp, event: Event, tags: Seq[String])

object PartialIntranetTask {
  def apply(task: IntranetTask, client: Client, event: Event, tags: Seq[String]): PartialIntranetTask =
    new PartialIntranetTask(task.name, task.priority, task.state, JsonClient(client), task.createdAt.get, event, tags)

  implicit val format = Json.format[PartialIntranetTask]
}

case class CompleteTaskLog(targetState: TaskState, createdBy: JsonClient, createdAt: Timestamp)

object CompleteTaskLog {
  def apply(log: IntranetTaskLog, client: Client): CompleteTaskLog = CompleteTaskLog(log.targetState, JsonClient(client), log.createdAt.get)

  implicit val format = Json.format[CompleteTaskLog]
}


case class CompleteTaskAssignationLog(assignee: JsonClient, deleted: Boolean, createdBy: JsonClient, createdAt: Timestamp)

object CompleteTaskAssignationLog {
  def apply(log: IntranetTaskAssignationLog, client: Client, assignee: Client): CompleteTaskAssignationLog =
    CompleteTaskAssignationLog(JsonClient(assignee), log.deleted, JsonClient(client), log.createdAt.get)

  implicit val format = Json.format[CompleteTaskAssignationLog]
}

case class CompleteTaskComment(content: String, createdBy: JsonClient, createdAt: Timestamp)

object CompleteTaskComment {
  def apply(com: IntranetTaskComment, client: Client): CompleteTaskComment = CompleteTaskComment(com.content, JsonClient(client), com.createdAt.get)

  implicit val format = Json.format[CompleteTaskComment]
}
case class CompleteIntranetTask(name: String,
                                priority: Int,
                                state: TaskState,
                                createdBy: JsonClient,
                                createdAt: Timestamp,
                                event: Event,
                                comments: Seq[CompleteTaskComment],
                                logs: Seq[CompleteTaskLog],
                                assignationLogs: Seq[CompleteTaskAssignationLog],
                                assignees: Seq[JsonClient],
                                tags: Seq[String])

object CompleteIntranetTask {
  def apply(task: IntranetTask, client: Client, event: Event, comments: Seq[CompleteTaskComment],
            logs: Seq[CompleteTaskLog],
            assignationLogs: Seq[CompleteTaskAssignationLog],
            assignees: Seq[Client],
            tags: Seq[String]): CompleteIntranetTask =
    CompleteIntranetTask(task.name, task.priority, task.state, JsonClient(client), task.createdAt.get, event, comments, logs, assignationLogs, assignees.map(c => JsonClient(c)), tags)

  implicit val format = Json.format[CompleteIntranetTask]

}

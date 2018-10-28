package data

import java.sql.Timestamp

/**
  * @author Louis Vialar
  */
case class CompleteIntranetTask(name: String,
                                priority: Int,
                                state: TaskState,
                                createdBy: Client,
                                createdAt: Timestamp,
                                event: Event,
                                comments: Seq[CompleteTaskComment],
                                logs: Seq[CompleteTaskLog],
                                assignees: Seq[Client],
                                tags: Seq[String])

object CompleteIntranetTask {
  def apply(task: IntranetTask, client: Client, event: Event, comments: Seq[CompleteTaskComment],
            logs: Seq[CompleteTaskLog],
            assignees: Seq[Client],
            tags: Seq[String]) =
    CompleteIntranetTask(task.name, task.priority, task.state, client, task.createdAt, event, comments, logs, assignees, tags)
}

case class PartialIntranetTask(task: IntranetTask, client: Client, event: Event, tags: Seq[String])

case class CompleteTaskLog(targetState: TaskState, createdBy: Client, createdAt: Timestamp)

object CompleteTaskLog {
  def apply(log: IntranetTaskLog, client: Client) = CompleteTaskLog(log.targetState, client, log.createdAt)
}

case class CompleteTaskComment(content: String, createdBy: Client, createdAt: Timestamp)

object CompleteTaskComment {
  def apply(com: IntranetTaskComment, client: Client) = CompleteTaskComment(com.content, client, com.createdAt)
}

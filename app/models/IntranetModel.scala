package models

import data.{Client, CompleteIntranetTask, CompleteTaskAssignationLog, CompleteTaskComment, CompleteTaskLog, Event, IntranetTask, IntranetTaskAssignation, IntranetTaskAssignationLog, IntranetTaskComment, IntranetTaskLog, IntranetTaskTag, PartialIntranetTask}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class IntranetModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {


  import profile.api._

  private val baseJoin =
    intranetTasks
      .join(clients).on(_.createdBy === _.id)
      .join(events).on(_._1.event === _.id)
      .map(pair => (pair._1._1, pair._1._2, pair._2))

  private val tagsJoinLeft = baseJoin.joinLeft(intranetTaskTags).on(_._1.id === _.taskId)
  private val tagsJoin = baseJoin.join(intranetTaskTags).on(_._1.id === _.taskId)


  /**
    * Create a task and its first comment
    *
    * @param task         the task to insert
    * @param firstComment the first comment to add (its taskId will be overwritten)
    * @return the id of the task inserted
    */
  def createTask(task: IntranetTask, firstComment: IntranetTaskComment, tags: Seq[String]): Future[Int] =
    db.run(
      (intranetTasks.returning(intranetTasks.map(_.id)) += task)
        .flatMap(id => {
          (intranetTaskComments += firstComment.copy(taskId = id)).map(_ => id)
        })
        .flatMap(id => {
          (intranetTaskTags ++= tags.map(tag => IntranetTaskTag(id, tag))).map(_ => id)
        })

    )

  def updatePriority(task: Int, taskData: Int) = {
    db.run(intranetTasks.filter(_.id === task).map(_.priority).update(taskData))
  }


  def postComment(comment: IntranetTaskComment): Future[Int] =
    db.run(intranetTaskComments.returning(intranetTaskComments.map(_.id)) += comment)

  def changeState(change: IntranetTaskLog): Future[Int] =
    db.run(
      intranetTasks.filter(_.id === change.taskId).map(_.state).update(change.targetState).andThen(
        intranetTaskLogs += change
      )
    )

  def addOrRemoveAssignee(log: IntranetTaskAssignationLog): Future[Int] =
    db.run(
      (if (log.deleted) intranetTaskAssignations.filter(a => a.userId === log.assignee && a.taskId === log.taskId).delete
      else intranetTaskAssignations += IntranetTaskAssignation(log.taskId, log.assignee))
      .andThen(
        intranetTaskAssignationLogs += log
      ))

  def addTags(task: Int, tags: Seq[String]): Future[Option[Int]] =
    db.run(intranetTaskTags ++= tags.map(tag => IntranetTaskTag(task, tag)))

  def removeTags(task: Int, tags: Seq[String]): Future[Int] =
    db.run(intranetTaskTags.filter(t => t.taskId === task && t.taskTag.inSet(tags)).delete)

  def getTask(task: Int): Future[Option[CompleteIntranetTask]] = {
    db.run(
      baseJoin.filter(_._1.id === task).result.headOption.flatMap(base =>
        intranetTaskComments.filter(_.taskId === task).join(clients).on(_.createdBy === _.id).result.flatMap(comments =>
          intranetTaskLogs.filter(_.taskId === task).join(clients).on(_.createdBy === _.id).result.flatMap(logs =>
          intranetTaskAssignationLogs.filter(_.taskId === task).join(clients).on(_.createdBy === _.id).join(clients).on(_._1.assignee === _.id).result.flatMap(assigLog =>
            intranetTaskTags.filter(_.taskId === task).result.flatMap(tags =>
              intranetTaskAssignations.filter(_.taskId === task).join(clients).on(_.userId === _.id).result.map(assignees => {
                base.map {
                  case (task: IntranetTask, client: Client, event: Event) =>
                    CompleteIntranetTask(task, client, event,
                      comments.map(pair => CompleteTaskComment(pair._1, pair._2)),
                      logs.map(pair => CompleteTaskLog(pair._1, pair._2)),
                      assigLog.map(pair => data.CompleteTaskAssignationLog(pair._1._1, pair._1._2, pair._2)),
                      assignees.map(_._2),
                      tags.map(_.tag)
                    )
                }
              }
              )
            )
          )
          )
        )
      )
    )
  }


  private def parseToTasks(v: Seq[((IntranetTask, Client, Event), Option[IntranetTaskTag])]): Seq[PartialIntranetTask] = v.groupBy(_._1)
    .mapValues(seq => seq.map(_._2.map(_.tag)).filter(_.isDefined).map(_.get))
    .map(pair => PartialIntranetTask(pair._1._1, pair._1._2, pair._1._3, pair._2)).toSeq

  def getTasksByEvent(event: Int): Future[Seq[PartialIntranetTask]] = {
    db.run(tagsJoinLeft.filter(_._1._1.event === event).result)
      .map(v => parseToTasks(v))
  }

  def getTasksByEventWithTags(event: Int, tags: Seq[String]): Future[Seq[PartialIntranetTask]] = {
    db.run(tagsJoin.filter(_._1._1.event === event).filter(_._2.taskTag.inSet(tags)).result)
      .map(v => parseToTasks(v.map(pair => (pair._1, Some(pair._2))))) // call parser but wrap in optionals
  }

}

package models

import data.Event
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class EventsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {


  import profile.api._

  type OrdersFilter = Query[Orders, data.Order, Seq] => Query[Orders, data.Order, Seq]

  def getEvents: Future[Seq[data.Event]] =
    db.run(events.result)

  def getEvent(id: Int): Future[data.Event] =
    db.run(events.filter(ev => ev.id === id).result.head)

  def createEvent(event: Event): Future[Int] =
    db.run(events.returning(events.map(e => e.id)) += event)

  def updateEvent(id: Int, event: Event): Future[Int] =
    db.run(events.filter(e => e.id === id).update(event))

}

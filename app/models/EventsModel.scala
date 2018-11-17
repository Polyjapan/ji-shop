package models

import com.mysql.jdbc.exceptions.jdbc4.MySQLIntegrityConstraintViolationException
import data.Event
import exceptions.HasItemsException
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.mvc.{Action, AnyContent}
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


  /**
    * Delete an event. One can delete an event only if it has no product.
    */
  def deleteEvent(id: Int): Future[Int] =
    // Won't succeed if there are still products existing in the event because of SQL constraints
    db.run(events.filter(e => e.id === id).delete).recoverWith {
      case _: MySQLIntegrityConstraintViolationException => throw HasItemsException()
    }
}

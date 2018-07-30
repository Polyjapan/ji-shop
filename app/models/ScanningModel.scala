package models

import java.sql.Timestamp
import java.time.Instant

import data.{ClaimedTicket, Client, Event, ScanningConfiguration, ScanningItem}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ScanningModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  private val configJoin = scanningConfigurations join scanningItems on (_.id === _.scanningConfigurationId)
  private val productsJoin = scanningConfigurations join scanningItems on (_.id === _.scanningConfigurationId) join products on (_._2.acceptedItemId === _.id)

  private def joinToPair(seq: Seq[(ScanningConfiguration, ScanningItem)]): Option[(ScanningConfiguration, Seq[ScanningItem])] = {
    if (seq.isEmpty) None
    else Some(seq.head._1, seq.map(_._2))
  }

  private def joinToPairWithProduct(seq: Seq[((ScanningConfiguration, ScanningItem), Product)]): Option[(ScanningConfiguration, Seq[Product])] = {
    if (seq.isEmpty) None
    else Some(seq.head._1._1, seq.map(_._2))
  }

  def getConfigs: Future[Seq[ScanningConfiguration]] = db.run(scanningConfigurations.result)

  def getConfig(id: Int): Future[Option[(ScanningConfiguration, Seq[ScanningItem])]] = db.run(configJoin.result).map(joinToPair)

  def getFullConfig(id: Int): Future[Option[(ScanningConfiguration, Seq[Product])]] = db.run(productsJoin.result).map(joinToPairWithProduct)

  def invalidateBarcode(ticketId: Int, userId: Int): Future[Int] = {
    val checkCodeStillValid = (claimedTickets join clients on (_.claimedBy === _.id)) // we join with the clients so that we can return useful information in the exception (i.e. the name)
      .filter(_._1.ticketId === ticketId)
      .result
      .flatMap(r =>
        if (r.isEmpty) DBIO.successful(Unit) // if no result returned: the ticket is still valid
        else DBIO.failed(new AlreadyValidatedTicketException(r.head._1, r.head._2))) // if a result was returned: the ticket is no longer valid and we return it

    val insertValidation = claimedTickets += ClaimedTicket(ticketId, Timestamp.from(Instant.now()), userId)

    db.run(checkCodeStillValid andThen insertValidation)
  }
}

case class AlreadyValidatedTicketException(claimedTicket: ClaimedTicket, claimedBy: Client) extends IllegalStateException

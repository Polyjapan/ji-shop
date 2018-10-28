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

  private val configJoin = scanningConfigurations joinLeft scanningItems on (_.id === _.scanningConfigurationId)
  private val productsJoin = scanningConfigurations join scanningItems on (_.id === _.scanningConfigurationId) join products on (_._2.acceptedItemId === _.id)
  private val productsLeftJoin = scanningConfigurations
    .joinLeft(scanningItems).on(_.id === _.scanningConfigurationId)
    .joinLeft(products).on(_._2.map(_.acceptedItemId) === _.id)

  def createConfig(config: ScanningConfiguration): Future[Int] = db.run(scanningConfigurations.returning(scanningConfigurations.map(_.id)) += config)

  def updateConfig(id: Int, config: ScanningConfiguration): Future[Int] = db.run(scanningConfigurations.filter(_.id === id).update(config))

  def addProduct(id: Int, productId: Int): Future[Int] = db.run(scanningItems += ScanningItem(id, productId))

  def removeProduct(id: Int, productId: Int): Future[Int] = db.run(scanningItems
    .filter(_.scanningConfigurationId === id)
    .filter(_.acceptedItemId === productId).delete)

  private def joinToPair(seq: Seq[(ScanningConfiguration, Option[ScanningItem])]): Option[(ScanningConfiguration, Seq[ScanningItem])] = {
    if (seq.isEmpty) None
    else Some(seq.head._1, seq.map(_._2).filter(_.isDefined).map(_.get))
  }

  private def joinToPairWithProduct(seq: Seq[(((ScanningConfiguration, Option[ScanningItem]), Option[data.Product]), Option[data.Event])]): Option[(ScanningConfiguration, Map[data.Event, Seq[data.Product]])] = {
    if (seq.isEmpty) None
    else {
      val map = seq.groupBy(_._2).filterKeys(opt => opt.isDefined).map(pair => (pair._1.get, pair._2))
      Some((seq.head._1._1._1, map.mapValues(seq => seq.map(_._1._2).filter(opt => opt.isDefined).map(_.get))))
    }
  }


  def getConfigsAcceptingProduct(event: Int, id: Int): Future[Seq[ScanningConfiguration]] =
    db.run(productsJoin.filter(pair => pair._2.id === id && pair._2.eventId === id).map(_._1._1).distinct.result)

  def getConfigs: Future[Seq[ScanningConfiguration]] = db.run(scanningConfigurations.result)

  def getConfig(id: Int): Future[Option[(ScanningConfiguration, Seq[ScanningItem])]] = db.run(configJoin.filter(el => el._1.id === id).result).map(joinToPair)

  def getFullConfig(id: Int): Future[Option[(ScanningConfiguration, Map[Event, Seq[data.Product]])]] =
    db.run(
      productsLeftJoin.filter(el => el._1._1.id === id)
        .joinLeft(events).on(_._2.map(_.eventId) === _.id).result).map(joinToPairWithProduct)

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

package models

import data.{Event}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ProductsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  private val productsJoin = events join products on (_.id === _.eventId)

  private val joinToMap: (Seq[(Event, data.Product)]) => Map[Event, Seq[data.Product]] =
    _.groupBy(_._1).mapValues(_.map(_._2))


  def getProducts: Future[Map[Event, Seq[data.Product]]] =
    db.run(productsJoin.filter(_._1.visible === true).filter(_._2.isVisible === true).result).map(joinToMap)

  def getProductsAdmin: Future[Map[Event, Seq[data.Product]]] =
    // Here we allow invisible products to be displayed
    db.run(productsJoin.filter(_._1.visible === true).result).map(joinToMap)

  def getMergedProducts: Future[Seq[data.Product]] =
    db.run(productsJoin.filter(_._1.visible === true).filter(_._2.isVisible === true).result).map(_.map(_._2))

  def splitTickets(map: Map[Event, Seq[data.Product]]): Map[Event, (Seq[data.Product], Seq[data.Product])] =
    map.mapValues(_.partition(_.isTicket))
}

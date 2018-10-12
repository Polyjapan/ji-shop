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

  def getMergedProducts(includeHidden: Boolean, includeHiddenEvents: Boolean = false): Future[Seq[data.Product]] =
    db.run(productsJoin.filter(_._1.visible === true || includeHiddenEvents).filter(_._2.isVisible === true || includeHidden).result).map(_.map(_._2))

  def splitTickets(map: Map[Event, Seq[data.Product]]): Map[Event, (Seq[data.Product], Seq[data.Product])] =
    map.mapValues(_.partition(_.isTicket))

  def getProducts(event: Int): Future[Seq[data.Product]] =
    db.run(products.filter(_.eventId === event).result)

  /**
    * Get or insert a map of product names to their ids. The names that are not found will be inserted and their ID will
    * be returned.
    * @param event the event id, used to filter the items and to insert new ones if needed
    * @param itemNames the names of the items
    * @return a map of the item name to its id, containing all keys present in the itemNames argument
    */
  def getOrInsert(event: Int, itemNames: Iterable[String]): Future[Map[String, Int]] = {
    getProducts(event).map(products => {
      // Convert all products from the database to a map
      // The map contains all the products in the event, but that's not an issue
      val map = products.map(p => (p.name, p.id.get)).toMap

      // We look for the items that are not yet in the map
      val missing = itemNames.filterNot(name => map.contains(name))

      (map, missing)
    }).flatMap {
      case (map, missing) =>
        // We generate products for the missing items
        val toInsert = missing.map(name => data.Product(None, name, 0D, "Automatic insert from external dump",
          "Automatic insert from external dump", -1, event, isTicket = true, freePrice = false, isVisible = false))

        if (toInsert.nonEmpty)
          // We insert them one by one
          db.run(DBIO.sequence(toInsert.map(item => (products.returning(products.map(p => p.id)) += item).map(id => (item.name, id))))).map(res => res.toMap ++ map)
          // db.run(products.returning(products.map(p => (p.name, p.id))) ++= toInsert).map(res => res.seq.toMap ++ map)
          // Commented out: illegal in mysql
        else Future(map)
    }
  }
}

package models

import data.{Event, Product}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{Action, AnyContent}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ProductsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._

  private val productsJoin = events join products on (_.id === _.eventId)

  private val joinToMap: Seq[(Event, data.Product)] => Map[Event, Seq[data.Product]] = _.groupMap(_._1)(_._2)


  def buildItemList(map: Map[Event, Seq[Product]]): List[JsObject] =
    map.filter(_._2.nonEmpty).map(pair => Json.obj("event" -> pair._1, "items" -> pair._2)).toList

  def getProducts: Future[Map[Event, Seq[data.Product]]] =
    db.run(productsJoin.filter(pair => pair._1.visible === true && pair._1.archived === false && pair._2.isVisible === true).result).map(joinToMap)

  def getProductsAdmin: Future[Map[Event, Seq[data.Product]]] =
  // Here we allow invisible products to be displayed
    db.run(productsJoin.filter(pair => pair._1.visible === true && pair._1.archived === false).result).map(joinToMap)

  def getAllProducts: Future[Map[Event, Seq[data.Product]]] =
  // Here we allow invisible products to be displayed, but still not archived ones
    db.run(productsJoin.filter(_._1.archived === false).result).map(joinToMap)

  def getMergedProducts(includeHidden: Boolean, includeHiddenEvents: Boolean = false): Future[Seq[data.Product]] =
    db.run(productsJoin.filter(pair => (pair._1.visible === true || includeHiddenEvents) && pair._1.archived === false).filter(_._2.isVisible === true || includeHidden).result).map(_.map(_._2))

  def splitTickets(map: Map[Event, Seq[data.Product]]): Map[Event, (Seq[data.Product], Seq[data.Product])] =
    map.view.mapValues(_.partition(_.isTicket)).toMap

  def getProducts(event: Int): Future[Seq[data.Product]] =
    db.run(products.filter(_.eventId === event).result)

  def getProduct(event: Int, id: Int): Future[data.Product] =
    getOptionalProduct(event, id).map(_.get)

  def getOptionalProduct(event: Int, id: Int): Future[Option[data.Product]] =
    db.run(products.filter(p => p.id === id && p.eventId === event).result.headOption)

  def createProduct(event: Int, product: data.Product): Future[Int] =
    db.run(products += product)

  def updateProduct(event: Int, id: Int, product: data.Product): Future[Int] =
    db.run(products.filter(p => p.id === id && p.eventId === event).update(product))

  /**
    * Clone the products from an event to an other one, and returns a map with oldProductId -> clonedProductId
    * @param sourceEvent
    * @param targetEvent
    * @return
    */
  def cloneProducts(sourceEvent: Int, targetEvent: Int): Future[Map[Int, Int]] =
    db.run(products.filter(p => p.eventId === sourceEvent).result
      .map(list => (list.map(prod => prod.copy(Option.empty, eventId = targetEvent)), list))
      .flatMap(pair => {
        val list = pair._1
        val old = pair._2.map(_.id.get)
        val ret = products returning products.map(_.id) ++= list

        ret.map(res => (old zip res).toMap)
      }))

  /**
    * Removes from the database all the products from an event that were not sold and that don't appear in a POS/Scan configuration
    * @param id the id of the event to purge
    */
  def purgeUnsoldProducts(id: Int): Future[Int] =
    db.run(products.filter(_.eventId === id)

      .joinLeft(orderedProducts).on((product, ordered) => product.id === ordered.productId) // Join to ordered products
      .filter(pair => pair._2.isEmpty) // Only keep products that have no order
      .map(_._1) // Only keep the products

      .joinLeft(scanningItems).on((product, scan) => product.id === scan.acceptedItemId) // Join to scanned products
      .filter(pair => pair._2.isEmpty) // Only keep products that are not in a scan config
      .map(_._1) // Only keep the products

      .joinLeft(posConfigItems).on((product, pos) => product.id === pos.itemId) // Join to POS products
      .filter(pair => pair._2.isEmpty) // Only keep products that are not in a POS config
      .map(_._1) // Only keep the products

        .map(_.id)
        .result.flatMap(res => products.filter(prod => prod.id inSet res).delete)) // Delete them

  /**
    * Get or insert a map of product names to their ids. The names that are not found will be inserted and their ID will
    * be returned.
    *
    * @param event     the event id, used to filter the items and to insert new ones if needed
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
          "Automatic insert from external dump", -1, event, isTicket = true, freePrice = false, isVisible = false, image = None))

        if (toInsert.nonEmpty)
        // We insert them one by one
          db.run(DBIO.sequence(toInsert.map(item => (products.returning(products.map(p => p.id)) += item).map(id => (item.name, id))))).map(res => res.toMap ++ map)
        // db.run(products.returning(products.map(p => (p.name, p.id))) ++= toInsert).map(res => res.seq.toMap ++ map)
        // Commented out: illegal in mysql
        else Future(map)
    }
  }
}

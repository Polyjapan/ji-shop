package models

import data.{Category, Event}
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

  private val productsJoin = (events join categories on (_.id === _.eventId)) join products on (_._2.id === _.categoryId)

  private val joinToMap: (Seq[((Event, Category), data.Product)]) => Map[Event, Map[Category, Seq[data.Product]]] =
    _.foldLeft(Map.empty[Event, Map[Category, Seq[data.Product]]])((map, el) => {
      val innerMap = map.getOrElse(el._1._1, Map.empty[Category, Seq[data.Product]])
      val innerSeq = innerMap.getOrElse(el._1._2, Seq.empty[data.Product])

      map + (el._1._1 -> (innerMap + (el._1._2 -> (innerSeq :+ el._2))))
    })

  def getProducts: Future[Map[Event, Map[Category, Seq[data.Product]]]] =
    db.run(productsJoin.filter(_._1._1.visible === true).result).map(joinToMap)

}

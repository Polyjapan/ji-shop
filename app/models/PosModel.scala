package models

import data.{Event, PosConfigItem, PosConfiguration, PosPaymentLog}
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{Json, OFormat}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class PosModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {


  import profile.api._

  private val configJoin = posConfigurations joinLeft posConfigItems on (_.id === _.configId)
  private val eventsJoin = posConfigurations join events on (_.eventId === _.id)
  private val productsJoin = posConfigurations join posConfigItems on (_.id === _.configId) join products on (_._2.itemId === _.id)
  private val productsLeftJoin = posConfigurations joinLeft posConfigItems on (_.id === _.configId) joinLeft products on (_._2.map(_.itemId) === _.id)

  def createConfig(config: PosConfiguration): Future[Int] = db.run(posConfigurations.returning(posConfigurations.map(_.id)) += config)

  def updateConfig(id: Int, config: PosConfiguration): Future[Int] = db.run(posConfigurations.filter(_.id === id).update(config))

  /**
    * Deletes a configuration recursively (all the bound items are removed from the configuration before)
    *
    * @param id the config to delete
    */
  def deleteConfig(id: Int): Future[Int] =
    db.run(posConfigItems.filter(_.configId === id).delete.andThen(posConfigurations.filter(_.id === id).delete))

  def insertLog(dbItem: PosPaymentLog): Future[Int] = db.run(posPaymentLogs += dbItem)

  def addProduct(item: PosConfigItem): Future[Int] = db.run(posConfigItems += item)

  def removeProduct(id: Int, productId: Int): Future[Int] = db.run(posConfigItems
    .filter(_.configId === id)
    .filter(_.itemId === productId)
    .delete)

  private def joinToPair(seq: Seq[(PosConfiguration, Option[PosConfigItem])]): Option[(PosConfiguration, Seq[PosConfigItem])] = {
    if (seq.isEmpty) None
    else Some(seq.head._1, seq.map(_._2).filter(_.isDefined).map(_.get))
  }

  private def joinToPairWithProduct(seq: Seq[((PosConfiguration, Option[PosConfigItem]), Option[data.Product])]): Option[(PosConfiguration, Seq[JsonPosConfigItem])] = {
    if (seq.isEmpty) None
    else Some((seq.head._1._1, seq
      .filter(pair => pair._1._2.isDefined && pair._2.isDefined)
      .map(pair => JsonPosConfigItem(pair._1._2.get, pair._2.get))))
  }

  def getConfigs: Future[Map[Event, Seq[PosConfiguration]]] =
    db.run(eventsJoin.filterNot(_._2.archived).result)
      .map(res => res.groupBy(_._2).mapValues(_.map(_._1)))

  def getConfigsForEvent(eventId: Int): Future[Seq[PosConfiguration]] =
    db.run(posConfigurations.filter(_.eventId === eventId).result)

  def getConfig(id: Int): Future[Option[(PosConfiguration, Seq[PosConfigItem])]] = db.run(configJoin.filter(el => el._1.id === id).result).map(joinToPair)

  def getFullConfig(id: Int): Future[Option[JsonGetConfigResponse]] =
    db.run(productsLeftJoin.filter(el => el._1._1.id === id).result)
      .map(joinToPairWithProduct)
      .map(opt => opt.map(pair => JsonGetConfigResponse(pair._1, pair._2)))

  def cloneConfigs(sourceEvent: Int, targetEvent: Int, productsIdMapping: Map[Int, Int]) = {
    db.run(
      posConfigurations.filter(p => p.eventId === sourceEvent).result
        .map(configs => configs.map(config => (config.id.get, config.copy(id = None, eventId = targetEvent))))
        .flatMap(configs => {
          (posConfigurations returning posConfigurations.map(_.id) ++= configs.map(_._2))
            .map(ids => configs.map(_._1) zip ids)
        })
        .flatMap(configs => {
          DBIO.sequence(
            configs.map {
              case (oldId, id) =>
                posConfigItems.filter(_.configId === oldId).result
                  .map(content => content.map(item => item.copy(configurationId = id, productId = productsIdMapping(item.productId))))
                  .flatMap(toInsert => posConfigItems ++= toInsert)
            }
          )
        })
    )
  }
}


case class JsonPosConfigItem(item: data.Product, row: Int, col: Int, color: String, fontColor: String)

case class JsonGetConfigResponse(config: PosConfiguration, items: Seq[JsonPosConfigItem])

object JsonPosConfigItem {
  def apply(item: PosConfigItem, product: data.Product): JsonPosConfigItem = {
    JsonPosConfigItem(product, item.row, item.col, item.color, item.fontColor)
  }

  implicit val posConfigItemFormat: OFormat[JsonPosConfigItem] = Json.format[JsonPosConfigItem]
}

object JsonGetConfigResponse {
  implicit val posJsonResponse: OFormat[JsonGetConfigResponse] = Json.format[JsonGetConfigResponse]
}


package models

import data.{PosConfigItem, PosConfiguration, PosPaymentLog}
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
  private val productsJoin = posConfigurations join posConfigItems on (_.id === _.configId) join products on (_._2.itemId === _.id)
  private val productsLeftJoin = posConfigurations joinLeft posConfigItems on (_.id === _.configId) joinLeft products on (_._2.map(_.itemId) === _.id)

  def createConfig(config: PosConfiguration): Future[Int] = db.run(posConfigurations.returning(posConfigurations.map(_.id)) += config)

  def updateConfig(id: Int, config: PosConfiguration): Future[Int] = db.run(posConfigurations.filter(_.id === id).update(config))

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

  def getConfigs: Future[Seq[PosConfiguration]] = db.run(posConfigurations.result)

  def getConfig(id: Int): Future[Option[(PosConfiguration, Seq[PosConfigItem])]] = db.run(configJoin.filter(el => el._1.id === id).result).map(joinToPair)

  def getFullConfig(id: Int): Future[Option[JsonGetConfigResponse]] =
    db.run(productsLeftJoin.filter(el => el._1._1.id === id).result)
      .map(joinToPairWithProduct)
      .map(opt => opt.map(pair => JsonGetConfigResponse(pair._1, pair._2)))
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


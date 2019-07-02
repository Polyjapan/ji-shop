package models

import data.Image
import javax.inject.Inject
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ImagesModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {


  import profile.api._

  def getCategories: Future[Seq[String]] =
    db.run(images.map(_.category).distinct.result)

  def getImages(category: String): Future[Seq[Image]] =
    db.run(images.filter(_.category === category).result)

  def getAllImages: Future[Seq[Image]] =
    db.run(images.result)

  def createImage(img: Image): Future[Image] =
    db.run(images returning images.map(_.id) += img).map(id => img.copy(Some(id)))
}

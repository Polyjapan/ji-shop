package controllers.admin

import java.nio.ByteBuffer
import java.nio.file.{Files, Paths}
import java.util.{Base64, UUID}

import constants.Permissions._
import constants.results.Errors._
import data.Image
import javax.imageio.ImageIO
import javax.inject.Inject
import models.ImagesModel
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.ExecutionContext

/**
  * @author Louis Vialar
  */
class UploadsController @Inject()(cc: ControllerComponents, images: ImagesModel)(implicit ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {
  lazy val uploadPath = conf.get[String]("polyjapan.images.path")
  lazy val uploadUrl = conf.get[String]("polyjapan.images.url")
  val allowedTypes = Set("image/jpeg", "image/png", "image/bmp")
  val maxSize = Math.pow(2, 10) * 150 // 150 kB

  /**
    * List all the image categories
    *
    * @return a list of categories
    */
  def listCategories: Action[AnyContent] = Action.async {
    images.getCategories.map(e => Ok(Json.toJson(e)))
  } requiresPermission ADMIN_ACCESS


  /**
    * Get all the images in a given category
    *
    * @return a list of the images in a category
    */
  def listCategory(category: String) = Action.async {
    images.getImages(category).map(e =>
      Ok(Json.toJson(
        // Add the url of the picture in the return type
        e.map(el => Json.toJson(el).as[JsObject].+("url" -> uploadUrl + el.name))
      )))
  } requiresPermission ADMIN_ACCESS

  /**
    * Upload a new image on the service
    */
  def uploadImage(category: String) = Action.async(parse.temporaryFile) { request =>
    def randomId: String = {
      // Create random UUID
      val uuid = UUID.randomUUID
      // Create byte[] for base64 from uuid
      val src = ByteBuffer.wrap(new Array[Byte](16)).putLong(uuid.getMostSignificantBits).putLong(uuid.getLeastSignificantBits).array
      // Encode to Base64 and remove trailing ==
      Base64.getUrlEncoder.encodeToString(src).substring(0, 22)
    }

    val file = request.body
    val name: String = file.path.getFileName.toString
    val ext: String = name.split(".").last
    val fileName: String = randomId + "." + ext

    val mime = Files.probeContentType(file.path)
    val size = Files.size(file.path)

    if (!allowedTypes(mime)) {
      BadRequest.asError("Invalid mime " + mime).asFuture
    } else if (size > maxSize) {
      BadRequest.asError("Max size is 150kB, actual is " + size).asFuture
    } else {
      val image = ImageIO.read(file.path.toFile)

      images.createImage(Image(None, category, fileName, image.getWidth(), image.getHeight, size.toInt)).map(r => {

        request.body.moveFileTo(Paths.get(uploadPath + fileName), replace = true)
        Ok(Json.toJson(r))
      })
    }
  } requiresPermission ADMIN_ACCESS
}

package controllers.admin

import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Files, Paths}
import java.util.{Base64, Scanner, UUID}

import constants.Permissions._
import constants.results.Errors._
import data.Image
import javax.imageio.ImageIO
import javax.inject.Inject
import models.ImagesModel
import play.api.Configuration
import play.api.libs.json.{JsObject, JsString, Json}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import utils.AuthenticationPostfix._
import utils.Implicits._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Louis Vialar
 */
class UploadsController @Inject()(cc: ControllerComponents, images: ImagesModel)(implicit ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {
  lazy val uploadPath = conf.get[String]("polyjapan.images.path")
  lazy val uploadUrl = conf.get[String]("polyjapan.images.url")
  val allowedTypes = Map("image/jpeg" -> ".jpg", "image/png" -> ".png", "image/bmp" -> ".bmp")
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
    images.getImages(category).map(e => {
      val lst: Seq[JsObject] = e.map(encodeImage)
      Ok(Json.toJson(lst))
    })
  } requiresPermission ADMIN_ACCESS

  private def encodeImage(img: Image): JsObject =
    Json.toJsObject(img) + ("url" -> JsString(uploadUrl + img.name))


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
    println("Starting new image upload... " + request.contentType + " - " + file.path)

    def getMime: String = {
      val mime = Files.probeContentType(file.path)

      if (mime == null) {
        val p = new ProcessBuilder("/usr/bin/file", "-b", "--mime-type", file.path.toAbsolutePath.toString).start()
        val os = p.getInputStream
        p.waitFor()

        val reader = new Scanner(new InputStreamReader(os))
        val res = reader.nextLine()
        reader.close()
        res
      } else mime
    }

    val mime = getMime
    val size = Files.size(file.path)

    if (!allowedTypes.keySet(mime)) {
      println("Error: invalid mime " + mime + ". Returning 400.")
      BadRequest.asError("Invalid mime " + mime).asFuture
    } else if (size > maxSize) {
      println("Error: invalid file size " + size + ". Returning 400.")
      BadRequest.asError("Max size is 150kB, actual is " + size).asFuture
    } else {
      val fileName: String = randomId + allowedTypes(mime)
      println(" --> all good, storing the image under " + fileName)

      val image = ImageIO.read(file.path.toFile)
      images.createImage(Image(None, category, fileName, image.getWidth(), image.getHeight, size.toInt)).map(r => {
        Future {
          request.body.moveFileTo(Paths.get(uploadPath + fileName), replace = true)
          Files.setPosixFilePermissions(Paths.get(uploadPath + fileName), PosixFilePermissions.fromString("rw-r--r--"))
        }

        Ok(Json.toJson(encodeImage(r)))
      })
    }
  } requiresPermission ADMIN_ACCESS
}

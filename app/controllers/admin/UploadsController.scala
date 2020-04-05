package controllers.admin

import ch.japanimpact.api.uploads.UploadsService
import ch.japanimpact.api.uploads.uploads.{Container, UploadRequest}
import constants.Permissions._
import data.Image
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import utils.AuthenticationPostfix._

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author Louis Vialar
 */
class UploadsController @Inject()(cc: ControllerComponents, uploads: UploadsService)(implicit ec: ExecutionContext, conf: Configuration) extends AbstractController(cc) {
  private lazy val appId: Int = conf.get[Int]("jiauth.appId")
  val allowedTypes = Map("image/jpeg" -> ".jpg", "image/png" -> ".png", "image/bmp" -> ".bmp")
  val maxSize = Math.pow(2, 10) * 150 // 150 kB

  private val whitelistedContainers = Set("product_images", "events_headers")

  /**
   * List all the image categories
   *
   * @return a list of categories
   */
  def listCategories: Action[AnyContent] = Action {
    Ok(Json.toJson(whitelistedContainers))
  } requiresPermission ADMIN_ACCESS


  /**
   * Get all the images in a given category
   *
   * @return a list of the images in a category
   */
  def listCategory(category: String) = Action.async {
    uploads.containers(appId)(category).listFiles.map {
      case Left(err) => InternalServerError
      case Right(imgs) => Ok(Json.toJson(imgs.map(upload => Image(Some(upload.uploadId), category, upload.url, upload.sizeBytes.toInt))))
    }
  } requiresPermission ADMIN_ACCESS

  /**
   * Upload a new image on the service
   */
  def uploadImage(category: String) = Action.async { request =>
    if (whitelistedContainers(category))
      uploads.containers(appId).getOrCreateContainer(Container(None, None, category, maxSize.toLong, allowedTypes)).flatMap {
        case Left(err) => Future.successful(InternalServerError)
        case Right(container) => container.startUpload(UploadRequest()).map {
          case Left(err) => InternalServerError
          case Right(ticket) => Ok(ticket.url)
        }
      }
    else Future.successful(Forbidden)
  } requiresPermission ADMIN_ACCESS
}

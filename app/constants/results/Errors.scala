package constants.results

import play.api.data.{Form, FormError}
import play.api.libs.json.Json
import play.api.libs.json.Json.JsValueWrapper
import play.api.mvc.Result
import play.api.mvc.Results._
import utils.Formats._

/**
  * @author zyuiop
  */
object Errors {

  type BaseResultType = Function[Any, Result]

  implicit class PostFixAsError(result: Result) {
    implicit def asFormErrorSeq(err: Seq[FormError]): Result =
      result match {
        case r: BaseResultType =>
          r(Json.obj("success" -> false, "errors" -> err))
        case _ => result
      }

    implicit def asFormError(err: FormError*): Result = asFormErrorSeq(err.toSeq)

    implicit def asError(err: String*): Result = asFormErrorSeq(err.toSeq.map(FormError("", _)))
  }

  def formError(err: Form[_]): Result = BadRequest.asFormErrorSeq(err.errors)

  def notAuthenticated: Result = Forbidden.asError("error.no_auth_token")

  def dbError: Result = InternalServerError.asError("error.db_error")

  def noPermissions: Result = Unauthorized.asError("error.no_permissions")

  def unknownError: Result = InternalServerError.asError("error.exception")

  def notFound(field: String = ""): Result = NotFound.asFormError(FormError(field, "error.not_found"))

}
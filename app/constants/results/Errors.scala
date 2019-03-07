package constants.results

import play.api.data.{Form, FormError}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import utils.Formats._
import constants.ErrorCodes

/**
  * @author zyuiop
  */
object Errors {

  implicit class PostFixAsError(result: Result) {
    //noinspection TypeCheckCanBeMatch
    //somehow, patternmatching doesn't work for this case
    def asFormErrorSeq(err: Seq[FormError]): Result = {
      if (result.isInstanceOf[Status]) {
        val r = result.asInstanceOf[Status]
        r(Json.obj("success" -> false, "errors" -> err))
      } else result
    }

    def asFormError(err: FormError*): Result = asFormErrorSeq(err.toSeq)

    def asError(err: String*): Result = asFormErrorSeq(err.toSeq.map(FormError("", _)))

    def asSuccess: Result = {
      result match {
        case r: Status =>
          r(Json.obj("success" -> true))
        case _ => result
      }
    }
  }

  def formError(err: Form[_]): Result = BadRequest.asFormErrorSeq(err.errors)

  def notAuthenticated: Result = Forbidden.asError(ErrorCodes.AUTH_MISSING)

  def dbError: Result = InternalServerError.asError(ErrorCodes.DATABASE)

  def noPermissions: Result = Unauthorized.asError(ErrorCodes.PERMS_MISSING)

  def unknownError: Result = InternalServerError.asError(ErrorCodes.UNKNOWN)

  def notFound(field: String = ""): Result = NotFound.asFormError(FormError(field, ErrorCodes.NOT_FOUND))

  def success: Result = Ok.asSuccess

}

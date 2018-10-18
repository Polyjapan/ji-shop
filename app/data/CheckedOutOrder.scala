package data

import play.api.libs.json._

case class CheckedOutOrder(items: Seq[CheckedOutItem], orderType: Option[Source])

object CheckedOutOrder {

  implicit val format = Json.format[CheckedOutOrder]
}
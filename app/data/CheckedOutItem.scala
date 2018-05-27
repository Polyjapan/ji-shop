package data

import play.api.libs.json.Json

case class CheckedOutItem(itemId: Int, itemAmount: Int, itemPrice: Option[Double])

object CheckedOutItem {
  implicit val format = Json.format[CheckedOutItem]
}
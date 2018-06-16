package data

import play.api.libs.json._

case class CheckedOutOrder(items: Seq[CheckedOutItem], orderType: Option[Source])

object CheckedOutOrder {
  implicit val sourceFormat = new Format[Source] {
    override def reads(json: JsValue): JsResult[Source] = json match {
      case JsString(str) => JsSuccess(Source(str))
      case _ => JsError("Invalid type")
    }

    override def writes(o: Source): JsValue = JsString(Source.unapply(o))
  }

  implicit val format = Json.format[CheckedOutOrder]
}
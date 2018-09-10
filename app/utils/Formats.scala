package utils

import play.api.data.FormError
import play.api.libs.json.{JsValue, Json, Writes}

/**
  * @author zyuiop
  */
package object Formats {
  implicit object FormErrorWrites extends Writes[FormError] {
    override def writes(o: FormError): JsValue = {
      val data = Json.obj(
        "key" -> Json.toJson(o.key),
        "messages" -> Json.toJson(o.messages)
      )

      o.args match {
        case values: Seq[JsValue] => data + ("args" -> Json.toJson(values.filter(_.isInstanceOf[JsValue])))
        case _ => data
      }
    }
  }
}

package data

import play.api.libs.json._

case class PaymentLog(paymentMethod: PaymentMethod, accepted: Boolean,
                    cardTransactionCode: Option[Int],
                    cardTransactionResultCode: Option[Int],
                    cardReceiptSend: Option[Boolean],
                    cardTransactionMessage: Option[String]) {

  def toDbItem(orderId: Int): PosPaymentLog = PosPaymentLog(None, orderId, paymentMethod, null, accepted,
    cardTransactionCode, cardTransactionResultCode, cardReceiptSend, cardTransactionMessage)
}

object PaymentLog {
  implicit val format = Json.format[PaymentLog]

  implicit val methodFormat = new Format[PaymentMethod] {
    override def reads(json: JsValue): JsResult[PaymentMethod] = json match {
      case JsString(str) => JsSuccess(PaymentMethod(str))
      case _ => JsError("Invalid type")
    }

    override def writes(o: PaymentMethod): JsValue = JsString(PaymentMethod.unapply(o))
  }
}
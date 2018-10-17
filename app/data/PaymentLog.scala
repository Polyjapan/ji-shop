package data

import play.api.libs.json._

case class PaymentLog(paymentMethod: PaymentMethod, accepted: Boolean,
                      cardTransactionMessage: Option[String],
                      cardTransactionCode: Option[String],
                      cardReceiptSend: Option[Boolean],
                      cardFailureCause: Option[String]) {

  def toDbItem(orderId: Int): PosPaymentLog = PosPaymentLog(None, orderId, paymentMethod, null, accepted,
    cardTransactionMessage, cardTransactionCode, cardReceiptSend, cardFailureCause)
}

object PaymentLog {
  implicit val methodFormat = new Format[PaymentMethod] {
    override def reads(json: JsValue): JsResult[PaymentMethod] = json match {
      case JsString(str) => JsSuccess(PaymentMethod(str))
      case _ => JsError("Invalid type")
    }

    override def writes(o: PaymentMethod): JsValue = JsString(PaymentMethod.unapply(o))
  }

  implicit val format = Json.format[PaymentLog]
}
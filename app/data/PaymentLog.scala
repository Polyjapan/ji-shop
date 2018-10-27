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


  implicit val format = Json.format[PaymentLog]
}
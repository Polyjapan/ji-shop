package ch.japanimpact.api.tickets

import com.google.inject.ImplementedBy
import models.OrdersModel.GeneratedBarCode

import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[TicketsAPIStub])
trait TicketsAPI {
  type Barcode = String

  // TODO -- must be very resilient and tolerate a crash
  def insertTicketForAccreditation(orderId: Int, userId: Int, accredId: Int): Future[Try[GeneratedBarCode]]

  def insertTicketForOrder(orderId: Int, userId: Int): Future[Try[GeneratedBarCode]]
}

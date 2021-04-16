package ch.japanimpact.api.tickets
import models.OrdersModel

import scala.concurrent.Future
import scala.util.{Failure, Try}

class TicketsAPIStub extends TicketsAPI {
  override def insertTicketForAccreditation(orderId: Int, userId: Int, accredId: Int): Future[Try[OrdersModel.GeneratedBarCode]] =
    Future.successful(Failure(new NotImplementedError()))

  override def insertTicketForOrder(orderId: Int, userId: Int): Future[Try[OrdersModel.GeneratedBarCode]] =
    Future.successful(Failure(new NotImplementedError()))
}

package ch.japanimpact.api.accreds

import com.google.inject.ImplementedBy
import models.OrdersModel.GeneratedBarCode

import scala.concurrent.Future
import scala.util.Try

@ImplementedBy(classOf[AccredsAPIStub])
trait AccredsAPI {
  type AccreditationId = Int

  // TODO -- must be very resilient and tolerate a crash
  def insertAccreditation(userId: Int, accreditationCategory: Int, accreditationValidityDays: Int): Future[Try[AccreditationId]]
}

package ch.japanimpact.api.accreds
import scala.concurrent.Future
import scala.util.{Failure, Try}

class AccredsAPIStub extends AccredsAPI {
  override def insertAccreditation(userId: AccreditationId, accreditationCategory: AccreditationId, accreditationValidityDays: AccreditationId): Future[Try[AccreditationId]] =
    Future.successful(Failure(new NotImplementedError()))
}

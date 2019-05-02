import ch.japanimpact.auth.api.AuthApi
import com.google.inject.{AbstractModule, Provides}
import com.hhandoko.play.pdf.PdfGenerator
import play.api.{Configuration, Environment}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.ws.WSClient

import scala.concurrent.ExecutionContext

class ApplicationModule extends AbstractModule with ScalaModule {

  /** Module configuration + binding */
  override def configure(): Unit = {}

  /**
   * Provides PDF generator implementation.
   *
   * @param env The current Play app Environment context.
   * @return PDF generator implementation.
   */
  @Provides
  def providePdfGenerator(env: Environment): PdfGenerator = {
    val pdfGen = new PdfGenerator(env)

    pdfGen
  }

  @Provides
  def provideAuthClient(ws: WSClient)(implicit ec: ExecutionContext, config: Configuration): AuthApi = AuthApi(ws)

}
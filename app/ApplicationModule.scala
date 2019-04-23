import ch.japanimpact.auth.api.AuthApi
import com.google.inject.{AbstractModule, Provides}
import com.hhandoko.play.pdf.PdfGenerator
import play.api.{Configuration, Environment}
import net.codingwell.scalaguice.ScalaModule
import play.api.libs.ws.WSClient

class ApplicationModule extends AbstractModule with ScalaModule {

  /** Module configuration + binding */
  def configure(): Unit = {}

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
  def provideAuthClient(config: Configuration, ws: WSClient): AuthApi = {
    val clientId: String = config.get[String]("jiauth.clientId")
    val clientSecret: String = config.get[String]("jiauth.clientSecret")
    val apiRoot: String = config.get[String]("jiauth.baseUrl")

    new AuthApi(ws, apiRoot, clientId, clientSecret)
  }

}
import com.google.inject.{AbstractModule, Provides}
import com.hhandoko.play.pdf.PdfGenerator
import play.api.Environment
import net.codingwell.scalaguice.ScalaModule

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

}
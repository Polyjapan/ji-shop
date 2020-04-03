import java.time.Clock

import com.google.inject.{AbstractModule, Provides}
import com.hhandoko.play.pdf.PdfGenerator
import play.api.Environment

class ApplicationModule extends AbstractModule {

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
  def provideClock() = Clock.systemUTC()
}
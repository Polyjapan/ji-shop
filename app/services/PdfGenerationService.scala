package services

import java.awt.image.BufferedImage
import java.io.{ByteArrayOutputStream, File, FileInputStream}
import java.util.Base64

import com.hhandoko.play.pdf.PdfGenerator
import javax.inject.Inject
import models.OrdersModel.{GeneratedBarCode, OrderBarCode, TicketBarCode}
import org.apache.commons.io.{FileUtils, IOUtils}
import org.krysalis.barcode4j.HumanReadablePlacement
import org.krysalis.barcode4j.impl.AbstractBarcodeBean
import org.krysalis.barcode4j.impl.code128.Code128Bean
import org.krysalis.barcode4j.impl.datamatrix.{DataMatrixBean, SymbolShapeHint}
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider
import play.api.Configuration
import play.api.mvc.{Action, AnyContent}

import scala.concurrent.ExecutionContext
import scala.io.Source

/**
  * @author zyuiop
  */
class PdfGenerationService @Inject()(pdfGen: PdfGenerator, config: Configuration)(implicit ec: ExecutionContext) {
  // val imageFile = new File("result.jpg")

  // println(s"Image will be loaded from ${imageFile.getAbsolutePath}")

  /*
  The poster image
   */
  // lazy val image: String = Base64.getEncoder.encodeToString(FileUtils.readFileToByteArray(imageFile))


  private def genCodes(code: String): (String, String, String) = {
    def getCode(bean: AbstractBarcodeBean, rotation: Int = 0, dpi: Int = 100): String = {
      val out = new ByteArrayOutputStream()
      val canvas = new BitmapCanvasProvider(out, "image/png", dpi, BufferedImage.TYPE_BYTE_BINARY, false, rotation)
      //Generate the barcode
      bean.generateBarcode(canvas, code)
      //Signal end of generation
      canvas.finish()

      Base64.getEncoder.encodeToString(out.toByteArray)
    }

    val top = new Code128Bean
    top.setMsgPosition(HumanReadablePlacement.HRP_NONE)
    top.setHeight(10) // This is way more a magic value that you would think, it doesn't work if h*dpi != 150k

    val classic = new DataMatrixBean

    (getCode(top, 90, 200), getCode(classic, 0, 1000), getCode(top, 0, 1000))
  }

  private def doGenPdf(ticket: TicketBarCode): Array[Byte] = {
    val codes = genCodes(ticket.barcode)


    pdfGen.toBytes(views.html.ticket(ticket.event, ticket.product, codes, ticket.barcode), "goodies_" + ticket.barcode + ".pdf", Seq())
  }

  private def doGenPdf(ticket: OrderBarCode): Array[Byte] = {
    val codes = genCodes(ticket.barcode)


    pdfGen.toBytes(views.html.orderTicket(ticket.event, ticket.products, ticket.order, codes, ticket.barcode), "ticket_" + ticket.barcode + ".pdf", Seq())
  }

  def genInvoice(user: data.Client, event: data.Event, order: data.Order, products: Seq[(data.OrderedProduct, data.Product)]): (String, Array[Byte]) = {
    val productsMap = products.groupBy(_._2)
      .mapValues(seq =>
        seq.map(_._1)
          .groupBy(op => (op.productId, op.paidPrice))
          .mapValues(_.size)
        .map(pair => (pair._2, pair._1._2)).toSeq)
    val fileName = "invoice_" + order.id + ".pdf"
    val pdf = pdfGen.toBytes(views.html.invoice(user, event, order, productsMap), fileName, Seq())

    (fileName, pdf)
  }

  def genPdf(ticket: GeneratedBarCode): (String, Array[Byte]) = ticket match {
    case a: TicketBarCode => ("ticket_" + a.barcode + ".pdf", doGenPdf(a))
    case a: OrderBarCode => ("goodies_" + a.order + ".pdf", doGenPdf(a))
    case _ => throw new UnsupportedOperationException
  }

}

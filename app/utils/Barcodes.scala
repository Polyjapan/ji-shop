package utils

object Barcodes {
  sealed class BarcodeType(id: Int) {
    def pair = (id, this)

    def getId = id
  }

  case object ProductCode extends BarcodeType(9)
  case object OrderCode extends BarcodeType(8)
  case object Invalid extends BarcodeType(0)

  private val types: Map[Int, BarcodeType] = List(ProductCode, OrderCode).map(_.pair).toMap

  def parseCode(code: String): BarcodeType =
    types.getOrElse(code.head.toString.toInt, Invalid)

}

package controllers

import javax.inject.Inject
import models.{ClientsModel, ProductsModel}
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.libs.mailer.MailerClient
import play.api.mvc.{Action, AnyContent, MessagesAbstractController, MessagesControllerComponents}
import utils.HashHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/**
  * @author zyuiop
  */
class ShoppingController @Inject()(cc: MessagesControllerComponents, clients: ClientsModel, products: ProductsModel, mailerClient: MailerClient, config: Configuration)(implicit ec: ExecutionContext) extends MessagesAbstractController(cc) with I18nSupport {

  def homepage: Action[AnyContent] = Action.async { implicit request => {
    products.getProducts.map(data => Ok(views.html.Shopping.homepage(products.splitTickets(data))))
  }
  }

  def checkout: Action[AnyContent] = Action.async { implicit request => {
    if (!request.hasBody || request.body.asFormUrlEncoded.isEmpty)
      return Future(BadRequest)

    val data = request.body.asFormUrlEncoded.get

    products.getMergedProducts.map(products => {
      val requestedProducts = products.map(p => (p, data.get(s"product-${p.id.get}-amt"))).toMap
        .filter(p => p._2.nonEmpty && p._2.get.nonEmpty)
        .mapValues(formVal => Try(formVal.get.head.toInt))
        .filter(p => p._2.isSuccess)
        .mapValues(tr => tr.get)
        .filter(p => p._2 > 0)

      val pricedProducts = requestedProducts.map{
        case (p: Product, amt: Int) if p.freePrice =>
          val strPrice: String = data.getOrElse(s"product-${p.id.get}-price", Seq.empty[String]).headOption.getOrElse("0")
          val price: Double = Try(strPrice.toDouble).getOrElse(0D)

          if (price < p.price) (p, (amt, p.price))
          else (p, (amt, price))
        case (p: Product, amt: Int) => (p, (amt, p.price))
      }

      // Create order

      return Ok
      }
    })

  })

  }

}

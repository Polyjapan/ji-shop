package models

import javax.inject.Inject
import data.{TicketTemplate, TicketTemplateComponent}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class TemplatesModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  type TemplateAndComponents = (TicketTemplate, Seq[TicketTemplateComponent])

  import profile.api._

  private class TicketTemplates(tag: Tag) extends Table[TicketTemplate](tag, "ticket_templates") {
    def id = column[Int]("ticket_template_id", O.PrimaryKey, O.AutoInc)

    def baseImage = column[String]("ticket_template_base_image", O.SqlType("VARCHAR(250)"))

    def templateName = column[String]("ticket_template_name", O.SqlType("VARCHAR(180)"), O.Unique)

    def barCodeX = column[Int]("ticket_template_barcode_x")

    def barCodeY = column[Int]("ticket_template_barcode_y")

    def barCodeWidth = column[Int]("ticket_template_barcode_width")

    def barCodeHeight = column[Int]("ticket_template_barcode_height")

    def * =
      (id.?, baseImage, templateName, barCodeX, barCodeY, barCodeWidth, barCodeHeight).shaped <> (TicketTemplate.tupled, TicketTemplate.unapply)
  }

  private class TicketTemplateComponents(tag: Tag) extends Table[TicketTemplateComponent](tag, "ticket_template_components") {
    def id = column[Int]("ticket_template_component_id", O.PrimaryKey, O.AutoInc)

    def templateId = column[Int]("ticket_template_id")

    def x = column[Int]("ticket_template_component_x")

    def y = column[Int]("ticket_template_component_y")

    def font = column[String]("ticket_template_component_font", O.SqlType("VARCHAR(250)"))

    def fontSize = column[Int]("ticket_template_component_font_size")

    def content = column[String]("ticket_template_component_content")

    def template = foreignKey("ticket_template_components_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def * =
      (id.?, templateId, x, y, font, fontSize, content).shaped <> (TicketTemplateComponent.tupled, TicketTemplateComponent.unapply)
  }

  private abstract class PairTable(_tableTag: Tag, _tableName: String) extends Table[(Int, Int)](_tableTag, _tableName) {
    def templateId: Rep[Int] = column[Int]("ticket_template_id")

    def otherId: Rep[Int]
  }

  private class TicketTemplatesByProduct(tag: Tag) extends PairTable(tag, "ticket_templates_by_product") {
    def template = foreignKey("ticket_templates_by_product_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def productId = column[Int]("product_id")

    def product = foreignKey("ticket_templates_by_product_product_fk", productId, products)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def otherId = productId

    def * = (templateId, productId)

    def pk = primaryKey("pk_ticket_templates_by_product", (templateId, productId))
  }

  private class TicketTemplatesByCategory(tag: Tag) extends PairTable(tag, "ticket_templates_by_category") {
    def template = foreignKey("ticket_templates_by_category_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def categoryId = column[Int]("category_id")

    def category = foreignKey("ticket_templates_by_category_category_fk", categoryId, categories)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def otherId = categoryId

    def * = (templateId, categoryId)

    def pk = primaryKey("pk_ticket_templates_by_category", (templateId, categoryId))
  }

  private class TicketTemplatesByEvent(tag: Tag) extends PairTable(tag, "ticket_templates_by_event") {
    def template = foreignKey("ticket_templates_by_event_template_fk", templateId, ticketTemplates)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def eventId = column[Int]("event_id")

    def event = foreignKey("ticket_templates_by_event_event_fk", eventId, events)(_.id, onUpdate = ForeignKeyAction.Cascade, onDelete = ForeignKeyAction.Cascade)

    def otherId = eventId

    def * = (templateId, eventId)

    def pk = primaryKey("pk_ticket_templates_by_event", (templateId, eventId))
  }

  private val ticketTemplateComponents = TableQuery[TicketTemplateComponents]
  private val ticketTemplates = TableQuery[TicketTemplates]
  private val templatesByProduct = TableQuery[TicketTemplatesByProduct]
  private val templatesByCategory = TableQuery[TicketTemplatesByCategory]
  private val templatesByEvent = TableQuery[TicketTemplatesByEvent]

  private val componentsJoin = ticketTemplates join ticketTemplateComponents on (_.id === _.templateId)

  /**
    * Create a template, returning its id in a future
    */
  def createTemplate(template: TicketTemplate): Future[Int] = db.run((ticketTemplates returning ticketTemplates.map(_.id)) += template)

  /**
    * Update a template, returning a success future
    */
  def updateTemplate(template: TicketTemplate): Future[Boolean] =
    db.run((for {tpl <- ticketTemplates} yield tpl).update(template)).map(_ > 0)

  /**
    * Create a component, returning its id in a future
    */
  def createComponent(component: TicketTemplateComponent): Future[Int] =
    db.run((ticketTemplateComponents returning ticketTemplateComponents.map(_.id)) += component)

  /**
    * Update a component, returning a success future
    */
  def updateComponent(component: TicketTemplateComponent): Future[Boolean] =
    db.run((for {cmp <- ticketTemplateComponents} yield cmp).update(component)).map(_ > 0)

  def deleteComponent(id: Int): Future[Boolean] = db.run(ticketTemplateComponents.filter(_.id === id).delete).map(_ > 0)

  def deleteTemplate(id: Int): Future[Boolean] = db.run(ticketTemplates.filter(_.id === id).delete).map(_ > 0)

  private val componentsJoinMapper: (Seq[(TicketTemplate, TicketTemplateComponent)]) => Map[TicketTemplate, Seq[TicketTemplateComponent]] =
    _.groupBy(pair => pair._1).mapValues(_.map(_._2))

  private val singleComponentJoinMapper: (Seq[(TicketTemplate, TicketTemplateComponent)]) => Option[TemplateAndComponents] =
    componentsJoinMapper(_).toList.headOption

  /**
    * Queries all templates with their components
    */
  def allTemplates: Future[Map[TicketTemplate, Seq[TicketTemplateComponent]]] =
    db.run(componentsJoin.result).map(componentsJoinMapper)

  /**
    * Queries a template and its component by its id
    */
  def templateById(id: Int): Future[Option[TemplateAndComponents]] =
    db.run(componentsJoin.filter(_._1.id === id).result).map(singleComponentJoinMapper)

  /**
    * Queries a template for a given product
    */
  def templateByProduct(productId: Int, categoryId: Int, eventId: Int): Future[Option[TemplateAndComponents]] =
  // We first try to query the templatesByProduct table
    db.run(componentsJoin.join(templatesByProduct).on(_._1.id === _.templateId).filter(_._2.productId === productId).result)
      .flatMap {
        // If we found a reply we return a future with it
        case s: Seq[((TicketTemplate, TicketTemplateComponent), (Int, Int))] if s.nonEmpty => Future(singleComponentJoinMapper(s.map(_._1)))
        // If not, we try to query the templatesByCategory table
        case _ => db.run(componentsJoin.join(templatesByCategory).on(_._1.id === _.templateId).filter(_._2.categoryId === categoryId).result)
      }.flatMap {
        // If we already had a reply, we just pass it on
        case opt: Option[TemplateAndComponents] => Future(opt)
        // If we found something, we return a future with it
        case s: Seq[((TicketTemplate, TicketTemplateComponent), (Int, Int))] if s.nonEmpty => Future(singleComponentJoinMapper(s.map(_._1)))
        // It not, we try to query the templatesByEvent table
        case _ => db.run(componentsJoin.join(templatesByEvent).on(_._1.id === _.templateId).filter(_._2.eventId === eventId).result)
      }.flatMap {
        // Same stuff as before
        case opt: Option[TemplateAndComponents] => Future(opt)
        case s: Seq[((TicketTemplate, TicketTemplateComponent), (Int, Int))] if s.nonEmpty => Future(singleComponentJoinMapper(s.map(_._1)))
        // If nothing found, we query the default component that should be on id 0
        case _ => db.run(componentsJoin.filter(_._1.id === 0).result)
      }.map {
        case opt: Option[TemplateAndComponents] => opt
        case s: Seq[(TicketTemplate, TicketTemplateComponent)] if s.nonEmpty => singleComponentJoinMapper(s)
        case _ => Option.empty
      }

  class MapAction(templateId: Int) {
    private def mapTemplate[T <: PairTable](query: TableQuery[T], id: Int): Future[Boolean] =
      db.run(query += (templateId, id)).map(_ > 0)

    def withProduct(id: Int): Future[Boolean] = mapTemplate(templatesByProduct, id)

    def withCategory(id: Int): Future[Boolean] = mapTemplate(templatesByCategory, id)

    def withEvent(id: Int): Future[Boolean] = mapTemplate(templatesByEvent, id)
  }

  class UnmapAction(templateId: Int) {
    private def unmapTemplate[T <: PairTable](query: TableQuery[T], id: Int): Future[Boolean] =
      db.run(query.filter(e => e.templateId === templateId && e.otherId === id).delete).map(_ > 0)

    def fromProduct(id: Int): Future[Boolean] = unmapTemplate(templatesByProduct, id)

    def fromCategory(id: Int): Future[Boolean] = unmapTemplate(templatesByCategory, id)

    def fromEvent(id: Int): Future[Boolean] = unmapTemplate(templatesByEvent, id)
  }

  def mapTemplate(templateId: Int): MapAction = new MapAction(templateId)

  def unmapTemplate(templateId: Int): UnmapAction = new UnmapAction(templateId)
}

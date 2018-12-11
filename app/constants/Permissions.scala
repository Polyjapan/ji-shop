package constants

/**
  * @author Louis Vialar
  */
object Permissions {
  /**
    * Force an order to be validated
    */
  val FORCE_VALIDATION = "admin.force_validation"

  /**
    * Permission to sell tickets using a cashdesk (with order type ON_SITE)
    */
  val SELL_ON_SITE = "staff.sell_on_site"

  /**
    * Permission to import tickets from an external source (with order type RESELLER)
    */
  val IMPORT_EXTERNAL = "admin.import_external"

  /**
    * Permission to export all tickets for an event to a FNAC-compatible list
    */
  val EXPORT_TICKETS = "admin.export_tickets"

  /**
    * Permission to generate free orders (gifts)
    */
  val GIVE_FOR_FREE = "admin.give_for_free"

  /**
    * Permission to see all the types of orders
    */
  val SEE_ALL_ORDER_TYPES = "admin.see_all_order_types"

  /**
    * Permission to view an order that belongs to an other user
    */
  val VIEW_OTHER_ORDER = "admin.view_other_order"

  /**
    * Permission to view deleted orders and tickets
    */
  val VIEW_DELETED_STUFF = "admin.view_deleted_stuff"

  /**
    * Permission to download a ticket that belongs to an other user
    */
  val VIEW_OTHER_TICKET = "admin.view_other_ticket"

  /**
    * Permission to scan a ticket
    */
  val SCAN_TICKET = "staff.scan_ticket"

  /**
    * Permission to modify scanning configurations
    */
  val ADMIN_SCAN_MANAGE = "admin.change_scanning_configurations"


  /**
    * Permission to see items that are marked as not visible
    */
  val SEE_INVISIBLE_ITEMS = "admin.see_invisible_items"

  /**
    * Permission to access the admin area
    */
  val ADMIN_ACCESS = "admin.access_dashboard"

  /**
    * Permission to manage the events
    */
  val ADMIN_EVENT_MANAGE = "admin.event_manage"

  /**
    * Permission to manage the products
    */
  val ADMIN_PRODUCTS_MANAGE = "admin.products_manage"

  /**
    * Permission to modify POS configurations
    */
  val ADMIN_POS_MANAGE = "admin.change_pos_configurations"

  /**
    * Permission to view sales statistics of an event
    */
  val ADMIN_VIEW_STATS = "admin.view_stats"

  /**
    * Access the intranet data
    */
  val INTRANET_VIEW = "intranet.staff.view"

  /**
    * Post a new task to the intranet
    */
  val INTRANET_TASK_POST = "intranet.staff.post_task"

  /**
    * Accept tasks in the intranet
    */
  val INTRANET_TASK_ACCEPT = "intranet.admin.accept_task"

  /**
    * Assign tasks to self
    */
  val INTRANET_TASK_TAKE = "intranet.staff.self_assign"


  /**
    * Unassign tasks to self
    */
  val INTRANET_TASK_LEAVE = "intranet.staff.self_unassign"

  /**
    * Assign or unassign tasks to other
    */
  val INTRANET_TASK_GIVE = "intranet.admin.assign_other"

  /**
    * Change the state of a task
    */
  val INTRANET_TASK_CHANGE_STATE = "intranet.admin.change_task_state"

  /**
    * Edit a tasks created by someone else
    */
  val INTRANET_TASK_EDIT = "intranet.admin.task_edit"

  /**
    * Permission to sell tickets in advance on a polyjapan controlled sales point
    */
  val SELL_IN_ADVANCE = "admin.sell_in_advance"

}

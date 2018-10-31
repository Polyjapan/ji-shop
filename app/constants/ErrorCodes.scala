package constants

/**
  * @author Louis Vialar
  */
object ErrorCodes {
  /*
  General errors
   */
  val DATABASE = "error.db_error"
  val UNKNOWN = "error.exception"
  val AUTH_MISSING = "error.no_auth_token"
  val PERMS_MISSING = "error.no_permissions"
  val NOT_FOUND = "error.not_found"

  /*
  Specific errors
   */
  val INSERT_FAILED = "error.insert_failed"

  /**
    * The order was already accepted
    */
  val ALREADY_ACCEPTED = "error.already_accepted"

  /**
    * The order doesn't contain any item
     */
  val NO_REQUESTED_ITEM = "error.no_requested_item"

  /**
    * At least one item in the order is out of stock
    */
  val OUT_OF_STOCK = "error.item_out_of_stock"

  /**
    * An item in the order doesn't exist in the database
    */
  val MISSING_ITEM = "error.missing_item"

  /**
    * The product is not allowed by this scanner
    */
  val PRODUCT_NOT_ALLOWED = "error.product_not_allowed"

  /**
    * The scanner doesn't accept order tickets
    */
  val PRODUCTS_ONLY = "error.product_only_configuration"

  /**
    * The provided barcode has already been scanned
    */
  val ALREADY_SCANNED = "error.ticket_already_scanned"

  /**
    * The user can't login because it has not confirmed its email
    */
  val EMAIL_NOT_CONFIRMED = "error.email_not_confirmed"

  /**
    * This email is already used on the site
    */
  val USER_EXISTS = "error.user_exists"

  /**
    * There are fields missing in the CSV file
    */
  val MISSING_FIELDS = "error.missing_fields"

  /**
    * An error regarding PolyBanking
    */
  def POLYBANKING(polybankingError: String) = s"error.polybanking.$polybankingError"

  val CAPTCHA = "error.captcha"

  /**
    * This order was not an on site order
    */
  val NOT_ON_SITE = "error.not_on_site"

  /**
    * The provided task state is invalid
    */
  val INVALID_STATE = "invalid_state"

}

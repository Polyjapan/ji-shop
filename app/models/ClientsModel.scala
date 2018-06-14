package models

import javax.inject.Inject
import data.Client
import org.apache.commons.lang3.Validate
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ClientsModel @Inject() (protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext)
  extends HasDatabaseConfigProvider[MySQLProfile] {

  import profile.api._


  private class Permissions(tag: Tag) extends Table[(Int, String)](tag, "permissions") {
    def userId = column[Int]("client_id")
    def permission = column[String]("permission", O.SqlType("VARCHAR(180)"))


    def user = foreignKey("permissions_client_fk", userId, clients)(_.id, onDelete = ForeignKeyAction.Cascade)

    def * = (userId, permission)

    def pk = primaryKey("pk_permissions", (userId, permission))
  }

  private val permissions = TableQuery[Permissions]
  private val clientsJoin = clients joinLeft permissions on (_.id === _.userId)

  type ClientAndPermissions = (Client, Seq[String])

  private val permsJoinMapper: (Seq[(Client, Option[(Int, String)])]) => Seq[ClientAndPermissions] =
    _.groupBy(pair => pair._1).mapValues(_.map(_._2).filter(_.nonEmpty).map(_.get._2)).toSeq

  private val singleClientPermsJoinMapper: (Seq[(Client, Option[(Int, String)])]) => Option[ClientAndPermissions] =
    permsJoinMapper(_).headOption


  /**
    * Query all the clients registered in database
    * @return a future holding a [[Seq]] of all registered [[Client]] and their permissions as a [[Seq]] of [[String]]
    */
  def allClients: Future[Seq[ClientAndPermissions]] = db.run(clientsJoin.result).map(permsJoinMapper)

  /**
    * Query a client by its email
    */
  def findClient(email: String): Future[Option[ClientAndPermissions]] =
    db.run(clientsJoin.filter(_._1.email === email).result).map(singleClientPermsJoinMapper)


  /**
    * Create a client
    * @param client the client to create
    * @return a future hodling the id of the inserted client
    */
  def createClient(client: Client): Future[Int] = db.run((clients returning clients.map(_.id)) += client)

  /**
    * Updates a client whose id is set
    * @param client the client to update/insert
    * @return the number of updated rows in a future
    */
  def updateClient(client: Client): Future[Int] = {
    Validate.isTrue(client.id.isDefined)
    db.run(clients.filter(_.id === client.id.get).update(client))
  }
}

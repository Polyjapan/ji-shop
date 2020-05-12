package models

import java.time.Clock

import com.google.common.base.Preconditions
import data.{AuthenticatedUser, Client}
import javax.inject.Inject
import pdi.jwt.JwtSession
import play.api.Configuration
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.mvc.Request
import slick.jdbc.MySQLProfile

import scala.concurrent.{ExecutionContext, Future}

/**
 * @author zyuiop
 */
class ClientsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext, configuration: Configuration, clock: Clock)
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
   *
   * @return a future holding a [[Seq]] of all registered [[Client]]
   */
  def allClients: Future[Seq[Client]] = db.run(clients.result)

  /**
   * Query all the clients registered in database having a given permission
   *
   * @param permission the permission to look for
   * @return a future holding a [[Seq]] of all registered [[Client]] having the given permission
   */
  def allClientsWithPermission(permission: String): Future[Seq[Client]] = db.run(clientsJoin.filter(_._2.map(_.permission) === permission).map(_._1).distinct.result)

  /**
   * Query a client by its email
   */
  def findClient(email: String): Future[Option[ClientAndPermissions]] =
    db.run(clientsJoin.filter(_._1.email === email).result).map(singleClientPermsJoinMapper)

  /**
   * Query a client by its cas ID
   */
  def findClientByCasId(casId: Int): Future[Option[Client]] =
    db.run(clients.filter(row => row.casId === casId).result.headOption)

  def getClient(id: Int): Future[Option[ClientAndPermissions]] =
    db.run(clientsJoin.filter(_._1.id === id).result).map(singleClientPermsJoinMapper)

  def addPermission(id: Int, permission: String): Future[Int] =
    db.run(permissions += (id, permission))

  def removePermission(id: Int, permission: String): Future[Int] =
    db.run(permissions.filter(pair => pair.permission === permission && pair.userId === id).delete)

  /**
   * Create a client
   *
   * @param client the client to create
   * @return a future hodling the id of the inserted client
   */
  def createClient(client: Client): Future[Int] = db.run((clients returning clients.map(_.id)) += client)

  /**
   * Updates a client whose id is set
   *
   * @param client the client to update/insert
   * @return the number of updated rows in a future
   */
  def updateClient(client: Client): Future[Int] = {
    Preconditions.checkArgument(client.id.isDefined)
    db.run(clients.filter(_.id === client.id.get).update(client))
  }

  def generateLoginResponse(client: Int)(implicit request: Request[_]): Future[String] = {
    getClient(client).map {
      case Some((client, perms)) =>
        val token = JwtSession() + ("user", AuthenticatedUser(client, perms))

        token.serialize
      case None => throw new IllegalStateException()
    }
  }
}

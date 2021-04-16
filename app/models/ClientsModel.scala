package models

import anorm.SqlParser._
import anorm._
import com.google.common.base.Preconditions
import data.{AuthenticatedUser, Client}
import pdi.jwt.JwtSession
import play.api.Configuration
import play.api.db.Database
import play.api.mvc.Request
import utils.SqlUtils

import java.time.Clock
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

/**
 * @author zyuiop
 */
class ClientsModel @Inject()(val database: Database)(implicit ec: ExecutionContext, configuration: Configuration, clock: Clock) {
  type ClientAndPermissions = (Client, Seq[String])

  /**
   * Query all the clients registered in database
   *
   * @return a future holding a [[Seq]] of all registered [[Client]]
   */
  def allClients: Future[Seq[Client]] = Future {
    database.withConnection { implicit conn =>
      SQL("SELECT * FROM clients").as(Client.parser.*)
    }
  }

  /**
   * Query all the clients registered in database having a given permission
   *
   * @param permission the permission to look for
   * @return a future holding a [[Seq]] of all registered [[Client]] having the given permission
   */
  def allClientsWithPermission(permission: String): Future[Seq[Client]] = Future {
    database.withConnection { implicit c =>
      SQL("SELECT clients.* FROM clients NATURAL JOIN permissions WHERE permission = {perm}")
        .on("perm" -> permission)
        .as(Client.parser.*)
    }
  }

  private def getClient(field: String, value: ParameterValue): Future[Option[ClientAndPermissions]] = Future {
    database.withConnection { implicit c =>
      SQL(s"SELECT clients.*, permissions.permission FROM clients NATURAL LEFT JOIN permissions WHERE $field = {v}")
        .on("v" -> value)
        .as((Client.parser ~ str("permission").?).*)
        .groupMapReduce(_._1)(_._2.toList)(_ ++ _)
        .headOption
    }
  }

  /**
   * Query a client by its email
   */
  def findClient(email: String): Future[Option[ClientAndPermissions]] = getClient("client_email", email)

  def getClient(id: Int): Future[Option[ClientAndPermissions]] = getClient("client_id", id)

  /**
   * Query a client by its cas ID
   */
  def findClientByCasId(casId: Int): Future[Option[Client]] = Future {
    database.withConnection { implicit c =>
      SQL("SELECT * FROM clients WHERE client_cas_user_id = {id}")
        .on("id" -> casId)
        .as(Client.parser.singleOpt)
    }
  }

  def addPermission(id: Int, permission: String): Future[Int] = Future {
    database.withConnection { implicit c =>
      SQL("INSERT INTO permissions(client_id, permission) VALUES ({id}, {perm})")
        .on("id" -> id, "perm" -> permission)
        .executeUpdate()
    }
  }

  def removePermission(id: Int, permission: String): Future[Int] = Future {
    database.withConnection { implicit c =>
      SQL("DELETE FROM permissions WHERE client_id = {id} AND permission = {perm}")
        .on("id" -> id, "perm" -> permission)
        .executeUpdate()
    }
  }

  /**
   * Create a client
   *
   * @param client the client to create
   * @return a future hodling the id of the inserted client
   */
  def createClient(client: Client): Future[Int] = Future {
    database.withConnection { implicit c =>
      SqlUtils.insertOne("clients", client)
    }
  }

  /**
   * Updates a client whose id is set
   *
   * @param client the client to update/insert
   * @return the number of updated rows in a future
   */
  def updateClient(client: Client): Future[Int] = {
    Preconditions.checkArgument(client.id.isDefined)
    Future {
      database.withConnection { implicit c =>
        SqlUtils.updateOne("clients", client, ignores = Set(""))
      }
    }
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

package models

import com.google.common.base.Preconditions
import data.{AuthenticatedUser, Client, RefreshToken, RefreshTokenLog}
import javax.inject.Inject
import pdi.jwt.{JwtClaim, JwtSession}
import play.api.Configuration
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.Request
import slick.jdbc.MySQLProfile

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

/**
  * @author zyuiop
  */
class ClientsModel @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit ec: ExecutionContext, configuration: Configuration)
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

  def createRefreshToken(client: Int)(implicit request: Request[_]): Future[RefreshToken] = {
    val userAgent = request.headers.get("User-Agent").getOrElse("unknown")
    val insert = refreshTokens returning refreshTokens.map(_.id) += RefreshToken(None, client, None, disabled = false, userAgent)
    db.run(insert.flatMap(id => refreshTokens.filter(_.id === id).result.head))
  }

  def refreshIdToken(tokenId: Int, tokenClient: Int)(implicit request: Request[_]): Future[Option[ClientAndPermissions]] = {
    val userAgent = request.headers.get("User-Agent").getOrElse("unknown")
    val ip = request.remoteAddress

    db.run(
      refreshTokens
        .filter(token => token.id === tokenId && token.clientId === tokenClient && token.disabled === false)
        .result
        .headOption
        .flatMap {
          case Some(token) =>
            // Insert
            (refreshTokenLogs += RefreshTokenLog(token.id.get, None, userAgent, ip)).map(_ => Some(token)) >>
              clientsJoin.filter(_._1.id === token.clientId)
                .result
          case None => DBIO.successful(Seq.empty)
        }
    ).map(singleClientPermsJoinMapper)
  }

  def generateLoginResponse(client: Int)(implicit request: Request[_]): Future[JsObject] = {
    def REFRESH_MAX_AGE(implicit conf: Configuration): Option[Long] = conf.getOptional[Duration]("play.http.refreshToken.maxAge").map(_.toSeconds)

    createRefreshToken(client).flatMap(rt => {
      val refreshToken = (REFRESH_MAX_AGE match {
        case Some(time) => JwtSession(JwtClaim().expiresIn(time))
        case None => JwtSession()
      }) ++ ("id" -> rt.id.get, "clientId" -> rt.clientId)

      refreshIdToken(rt.id.get, rt.clientId).map(res => {
        val (client, perms) = res.get
        val idToken = JwtSession() + ("user", AuthenticatedUser(client, perms))
        Json.obj("success" -> true, "requireInfo" -> false, "errors" -> JsArray(), "refreshToken" -> refreshToken.serialize, "idToken" -> idToken.serialize)
      })
    })
  }
}

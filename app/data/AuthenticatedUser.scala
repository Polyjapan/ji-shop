package data

import play.api.libs.json.Json

/**
  * @author zyuiop
  */
case class AuthenticatedUser(id: Int, lastname: String, firstname: String, email: String, permissions: Seq[String])

object AuthenticatedUser {
  def apply(client: Client, perms: Seq[String]): AuthenticatedUser =
    AuthenticatedUser(client.id.get, client.lastname, client.firstname, client.email, perms)

  implicit val format = Json.format[AuthenticatedUser]
}

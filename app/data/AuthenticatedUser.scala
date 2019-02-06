package data

import play.api.libs.json.Json

/**
  * @author zyuiop
  */
case class AuthenticatedUser(id: Int, lastname: String, firstname: String, email: String, permissions: Seq[String]) {
  def hasPerm(perm: String) = {
    if (hasPermExclusion(perm)) false
    else if (permissions.contains(perm)) true
    else {
      // Check other permissions (i.e. admin.* for admin.sth)
      val list = perm.split("\\.").toList

      if (list.size < 2) false
      else list.map(_ + ".").scanLeft("")(_ + _) take list.size map (_ + "*") exists permissions.contains
    }
  }

  def hasPermExclusion(perm: String) = {
    permissions.contains(s"-$perm")
  }
}

object AuthenticatedUser {
  def apply(client: Client, perms: Seq[String]): AuthenticatedUser =
    AuthenticatedUser(client.id.get, client.lastname, client.firstname, client.email, perms)

  implicit val format = Json.format[AuthenticatedUser]
}

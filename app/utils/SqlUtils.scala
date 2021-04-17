package utils

import anorm.Macro.ColumnNaming
import anorm.SqlParser.scalar
import anorm.{BatchSql, Column, NamedParameter, SQL, ToParameterList, ToStatement}

import java.sql.Connection
import scala.language.implicitConversions

object SqlUtils {
  implicit def enumToColumn(enum: Enumeration): Column[enum.Value] = Column.columnToString.map(s => enum.withName(s))
  implicit def enumToStatement(enum: Enumeration): ToStatement[enum.Value] = (s, index, v) => s.setString(index, v.toString)

  /**
   * Inserts one item in the given table and returns its id
   *
   * @param table the table in which the item shall be inserted
   * @param item  the item that shall be inserted
   * @return the id of the inserted item
   */
  def insertOne[T](table: String, item: T)(implicit parameterList: ToParameterList[T], conn: Connection): Int = {
    val params: Seq[NamedParameter] = parameterList(item);
    val names: List[String] = params.map(_.name).toList
    val colNames = names.map(ColumnNaming.SnakeCase) mkString ", "
    val placeholders = names.map { n => s"{$n}" } mkString ", "

    SQL("INSERT INTO " + table + "(" + colNames + ") VALUES (" + placeholders + ")")
      .bind(item)
      .executeInsert(scalar[Int].single)
  }

  /**
   * Updates one item in the given table and returns the number of updated rows
   *
   * @param table the table in which the item shall be updated
   * @param item  the item that shall be updated
   * @param ignores a set of fields names that should be ignored
   * @param indexes the names of the index fields (first field by default)
   * @return the id of the inserted item
   */
  def updateOne[T](table: String, item: T, ignores: Set[String] = Set(), indexes: Set[String] = Set())(implicit parameterList: ToParameterList[T], conn: Connection): Int = {
    val params: Seq[NamedParameter] = parameterList(item)

    val indexesSet = if (indexes.nonEmpty) indexes else Set(params.head.name)

    val names: List[String] = params.map(_.name).filterNot(ignores).toList
    val setInstr = names zip names.map(ColumnNaming.SnakeCase) map { case (field, column) => s"$column = {$field}"} mkString ", "
    val whereInstr = indexesSet zip indexesSet.map(ColumnNaming.SnakeCase) map { case (field, column) => s"$column = {$field}"} mkString " AND "

    SQL(s"UPDATE $table SET $setInstr WHERE $whereInstr")
      .bind(item)
      .executeUpdate()
  }

  /**
   * Inserts items in the given table
   *
   * @param table the table in which the items shall be inserted
   * @param items the items that shall be inserted
   */
  def insertMultiple[T](table: String, items: Iterable[T])(implicit parameterList: ToParameterList[T], conn: Connection) = {
    val params: Seq[NamedParameter] = parameterList(items.head);
    val names: List[String] = params.map(_.name).toList
    val colNames = names.map(ColumnNaming.SnakeCase) mkString ", "
    val placeholders = names.map { n => s"{$n}" } mkString ", "

    BatchSql("INSERT INTO " + table + "(" + colNames + ") VALUES (" + placeholders + ")", params, items.tail.map(parameterList).toSeq:_*)
      .execute()
  }
}

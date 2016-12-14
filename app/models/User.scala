package models

import javax.inject.Inject

import play.api.Logger
import play.api.db.slick.DatabaseConfigProvider
import slick.ast.NumericTypedType
import slick.driver.JdbcProfile
import slick.dbio
import slick.dbio.Effect.Read
import slick.lifted.ColumnOrdered

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import helpers.PasswordHash

case class UserId(value: Long) extends AnyVal

case class User(
  id: UserId,
  userName: String,
  passwordHash: Long,
  salt: Long
);

class UserRepo @Inject()(
  private val dbConfigProvider: DatabaseConfigProvider,
  passwordHash: PasswordHash
) {
  val logger = Logger(getClass)
  val dbConfig = dbConfigProvider.get[JdbcProfile]
  val db = dbConfig.db
  import dbConfig.driver.api._
  private val Users = TableQuery[UserTable]

  implicit val userIdColumnType = MappedColumnType.base[UserId, Long](
    { _.value },
    { UserId.apply }
  )

  def create(user: User): Future[User] = {
    db.run(Users returning Users.map(_.id) += user).map { id =>
      user.copy(id = id)
    }
  }

  def get(id: UserId): Future[Option[User]] =
    db.run(Users.filter(_.id === id).result).map(_.headOption)

  def update(user: User): Future[Int] =
    db.run(Users.filter(_.id === user.id).update(user))

  def remove(id: UserId): Future[Int] =
    db.run(Users.filter(_.id === id).delete)

  def all(page: Int, pageSize: Int, orderBy: OrderBy): Future[PagedRecords[User]] =
    for {
      count <- count()
      records <- db.run(Users.sortBy(sort(orderBy)).drop(page * pageSize).take(pageSize).to[List].result)
    } yield PagedRecords(page, pageSize, (count + pageSize - 1) / pageSize, orderBy, records)

  def count(): Future[Int] = db.run(Users.length.result)

  def login(userName: String, password: String): Future[Option[UserId]] = db.run(
    Users.filter(_.userName === userName).result
  ).map { records =>
    records.headOption.flatMap { user =>
      if (passwordHash.generate(password, user.salt) == user.passwordHash) {
        logger.error("User " + user.id + " login successful.")
        Some(user.id)
      }
      else {
        logger.error("Password for " + user.id + " does not match.")
        None
      }
    }
  }

  private def sort(orderBy: OrderBy): UserTable => ColumnOrdered[_] = ut => {
    val col: ColumnOrdered[_] = orderBy.columnName match {
      case "users.id" => ut.id
      case "users.user_name" => ut.userName
      case cn: String =>
        logger.error("Invalid sort column name '" + cn + "'. Use user.id instead.")
        ut.id
    }
    if (orderBy.order == Desc) col.desc else col
  }

  private class UserTable(tag: Tag) extends Table[User](tag, "users") {
    def id = column[UserId]("id", O.AutoInc, O.PrimaryKey)
    def userName = column[String]("user_name")
    def passwordHash = column[Long]("password_hash")
    def salt = column[Long]("salt")
    def * = (id, userName, passwordHash, salt) <> (User.tupled, User.unapply)
  }
}

package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import play.api.data.validation.Constraints._
import play.api.data._
import play.api.data.Forms._
import javax.inject._
import play.api._
import play.api.mvc._
import play.api.i18n.{I18nSupport, Lang, MessagesApi, Messages}
import models._
import helpers._

case class Login(userName: String, password: String)

@Singleton
class UserController @Inject()(
  val messagesApi: MessagesApi,
  val userRepo: UserRepo,
  passwordHash: PasswordHash
) extends Controller with I18nSupport with AuthenticatedSupport {
  val logger: Logger = Logger(getClass)
  val AdminUserName = "administrator"

  def userForm(implicit lang: Lang) = Form(
    mapping(
      "userName" -> text.verifying(minLength(8), maxLength(24)),
      "password" -> tuple(
        "main" -> text.verifying(minLength(8), maxLength(24)),
        "confirm" -> text.verifying(minLength(8), maxLength(24))
      ).verifying(
        Messages("confirmPasswordDoesNotMatch"), passwords => passwords._1 == passwords._2
      )
    )(
      (userName, passwords) => {
        val (hash, salt) = passwordHash.generateWithSalt(passwords._1)
        User(UserId(0), userName, hash, salt)
      }
    )(
      u => Some(
        (u.userName, ("", ""))
      )
    )
  )

  val loginForm = Form(
    mapping(
      "userName" -> text.verifying(minLength(8), maxLength(24)),
      "password" -> text.verifying(minLength(8), maxLength(24))
    )(Login.apply)(Login.unapply)
  )

  val removeForm = Form(
    single("id" -> longNumber)
  )

  def index(page: Int, pageSize: Int, orderBySpec: String) = authenticated.async { implicit req =>
    implicit val loginUser = req.authenticatedUser

    userRepo.all(page, pageSize, OrderBy(orderBySpec)).map { records => {
      Ok(views.html.users(records, removeForm))
    }}
  }

  def startCreateUser = authenticated { implicit req =>
    implicit val loginUser = req.authenticatedUser

    Ok(views.html.createUser(userForm))
  }

  def createUser = authenticated.async { implicit req =>
    implicit val loginUser = req.authenticatedUser

    userForm.bindFromRequest.fold(
      formWithError => {
        logger.error("UserController.createUser validation error " + formWithError)
        Future.successful(BadRequest(views.html.createUser(formWithError)))
      },
      newUser => userRepo.create(newUser).map { rec =>
        Redirect(routes.UserController.index()).flashing("message" -> Messages("userCreated"))
      }.recover {
        case e: org.h2.jdbc.JdbcSQLException =>
          BadRequest(views.html.createUser(userForm.fill(newUser).withError("userName", "userNameAlreadyTaken")))
      }
    )
  }

  def editUser(id: Long) = authenticated.async { implicit req =>
    implicit val loginUser = req.authenticatedUser

    userRepo.get(UserId(id)).map { recOpt =>
      recOpt.map { rec =>
        Ok(views.html.editUser(UserId(id), userForm.fill(rec)))
      }.getOrElse(
        Redirect(routes.UserController.index()).flashing("message" -> Messages("unknownError"))
      )
    }
  }

  def updateUser(id: Long) = authenticated.async { implicit req =>
    implicit val loginUser = req.authenticatedUser

    userForm.bindFromRequest.fold(
      formWithError => {
        logger.error("UserController.updateUser validation error " + formWithError)
        Future.successful(BadRequest(views.html.editUser(UserId(id), formWithError)))
      },
      newUser => userRepo.update(newUser.copy(id = UserId(id))).map { rec =>
        Redirect(routes.UserController.index()).flashing("message" -> Messages("userUpdated"))
      }.recover {
        case e: org.h2.jdbc.JdbcSQLException =>
          BadRequest(
            views.html.editUser(UserId(id), userForm.fill(newUser).withError("userName", "userNameAlreadyTaken"))
          )
      }
    )
  }

  def removeUser = authenticated.async { implicit req =>
    implicit val loginUser = req.authenticatedUser

    removeForm.bindFromRequest.fold(
      formWithError => {
        logger.error("UserController.removeUser validation error " + formWithError)
        Future.successful(Redirect(routes.UserController.index()).flashing("message" -> Messages("unknownError")))
      },
      id => userRepo.remove(UserId(id)).map { updateCount =>
        Redirect(routes.UserController.index()).flashing("message" -> Messages("userRemoved"))
      }
    )
  }

  def startLogin(url: String) = Action.async { implicit req =>
    logger.info("StartLogin(" + url + ")")
    userRepo.count().map { userCount =>
      if (userCount == 0) {
        logger.info("No users found. Creating first user.")
        val password = passwordHash.password()
        val (salt, hash) = passwordHash.generateWithSalt(password)
        userRepo.create(User(UserId(0), AdminUserName, hash, salt)).map { user =>
          logger.info("--------------------")
          logger.info("Your first user '" + AdminUserName + "' has password '" + password + "'")
          logger.info("--------------------")
        }

        Ok(
          views.html.login(
            loginForm.fill(Login(AdminUserName, "")).discardingErrors.withGlobalError(Messages("checkLogFileForAdminPassword")),
            url
          )
        )
      }
      else {
        Ok(views.html.login(loginForm, url))
      }
    }
  }

  def login(url: String) = Action.async { implicit req =>
    loginForm.bindFromRequest.fold(
      formWithError => {
        logger.error("UserController.login validation error " + formWithError)
        Future.successful(BadRequest(views.html.login(formWithError, url)))
      },
      login => userRepo.login(login.userName, login.password).map {
        case None => BadRequest(
          views.html.login(loginForm.fill(login).withGlobalError(Messages("userAndPasswordNotMatched")), url)
        )
        case Some(userId) =>
          Redirect(Sanitizer.forUrl(url)).withSession(req.session + LoginSession.loginSessionString(userId.value))
      }
    )
  }

  def logoff = authenticated { implicit req =>
    Redirect(routes.HomeController.index).withSession(req.session - LoginSession.LoginSessionKey)
  }
}

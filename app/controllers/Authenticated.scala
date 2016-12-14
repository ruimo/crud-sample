package controllers

import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Results._
import scala.concurrent.duration.Duration
import scala.concurrent.duration._
import play.api.mvc._
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import models._

case class LoginSession(userId: Long, expirationTime: Long) {
  val serialized = userId + ";" + expirationTime
  def isExpired(now: Long = System.currentTimeMillis): Boolean = now > expirationTime
  def renewExpirationTime(now: Long = System.currentTimeMillis): LoginSession = copy(
    expirationTime = now + LoginSession.SessionTimeout.toMillis
  )
  def toLoginSessionString: (String, String) = LoginSession.LoginSessionKey -> serialized
}

object LoginSession {
  val LoginSessionKey: String = "login"
  val SessionTimeout: Duration = 6.hour

  def retrieveLogin(requestHeader: RequestHeader): Option[LoginSession] =
    requestHeader.session.get(LoginSessionKey).map(LoginSession.apply)

  def retrieveLogin(result: Result)(implicit requestHeader: RequestHeader): Option[LoginSession] =
    result.session.get(LoginSessionKey).map(LoginSession.apply)

  def loginSessionString(userId: Long, now: Long = System.currentTimeMillis): (String, String) =
    LoginSession(userId, 0).renewExpirationTime(now).toLoginSessionString

  def apply(sessionString: String): LoginSession = {
    val args = sessionString.split(';').map(_.toLong)
    LoginSession(args(0), args(1))
  }
}

class AuthenticatedRequest[+U, +A](
  val authenticatedUser: U,
  request: Request[A]
) extends WrappedRequest[A](request)

class AuthenticatedBuilder[U](
  retrieveUser: RequestHeader => Future[Option[U]],
  onUnauthorized: RequestHeader => Result = _ => Unauthorized(views.html.defaultpages.unauthorized())
) extends ActionBuilder[({ type R[A] = AuthenticatedRequest[U, A] })#R] {
  val logger = Logger(getClass)

  def invokeBlock[A](request: Request[A], block: (AuthenticatedRequest[U, A]) => Future[Result]) =
    authenticate(request, block)

  def authenticate[A](request: Request[A], block: (AuthenticatedRequest[U, A]) => Future[Result]) = {
    retrieveUser(request).flatMap {
      case None => {
        logger.error("User not logged in. Request login page.")
        Future.successful(onUnauthorized(request))
      }
      case Some(user) => block(new AuthenticatedRequest[U, A](user, request))
    }
  }
}

case class LoginUser(
  session: LoginSession,
  user: User
)

trait AuthenticatedSupport {
  def userRepo: UserRepo
  def onUnauthorized(request: RequestHeader) =
    Redirect(
      routes.UserController.startLogin(if (request.method.equalsIgnoreCase("get")) request.uri else "/")
    )
  implicit def loginUser(implicit request: RequestHeader): Future[Option[LoginUser]] =
    LoginSession.retrieveLogin(request) match {
      case None => Future.successful(None)
      case Some(loginSession) => {
        if (loginSession.isExpired()) Future.successful(None)
        else userRepo.get(UserId(loginSession.userId)).map { userOpt =>
          userOpt.map { user => LoginUser(loginSession, user) }
        }
      }
    }

  def authenticated = new AuthenticatedBuilder[LoginUser](
    req => loginUser(req),
    onUnauthorized
  )
}

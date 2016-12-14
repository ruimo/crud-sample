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

@Singleton
class HomeController @Inject()(
  val messagesApi: MessagesApi,
  val userRepo: UserRepo
) extends Controller with I18nSupport with AuthenticatedSupport {
  val logger: Logger = Logger(getClass)

  def index = Action.async { implicit req =>
    loginUser.map { implicit loginUser =>
      Ok(views.html.index())
    }
  }
}

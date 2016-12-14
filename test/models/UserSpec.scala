package models

import java.util.concurrent.TimeUnit

import play.api.test._
import play.api.test.Helpers._
import org.specs2.mutable._
import play.api.Application
import play.api.i18n.{Lang, Messages, MessagesApi}
import play.api.inject.guice.GuiceApplicationBuilder
import models._
import org.specs2.specification.BeforeAfterEach

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class UserSpec extends Specification with BeforeAfterEach {
  private var userRepo: UserRepo = _
  def before = {
    val app = new GuiceApplicationBuilder().configure(
      "slick.dbs.default.db.url" -> "jdbc:h2:mem:test;DATABASE_TO_UPPER=false;TRACE_LEVEL_SYSTEM_OUT=2"
    ).build()
    userRepo = app.injector.instanceOf[UserRepo]
  }
  def after = userRepo.db.shutdown

  "User" should {
    "Can create message record" in {
      val asyncTest = for {
        created01 <- userRepo.create(User(UserId(0), "name02", 0, 0))
        created02 <- userRepo.create(User(UserId(0), "name01", 0, 0))
        list <- userRepo.all(0, 10, OrderBy("users.user_name", Asc))
      } yield {
        list.records.size === 2
        list.records(0) === created02
        list.records(1) === created01
      }

      Await.result(asyncTest, 10.seconds)
    }

    "Can window records" in {
      val asyncTest = for {
        created01 <- userRepo.create(User(UserId(0), "name02", 0, 0))
        created02 <- userRepo.create(User(UserId(0), "name01", 0, 0))
        list1 <- userRepo.all(0, 1, OrderBy("users.user_name", Asc))
        list2 <- userRepo.all(1, 1, OrderBy("users.user_name", Asc))
      } yield {
        list1.records.size === 1
        list1.records(0) === created02

        list2.records.size === 1
        list2.records(0) === created01
      }

      Await.result(asyncTest, 10.seconds)
    }

    "Can get record by id" in {
      val asyncTest = for {
        created01 <- userRepo.create(User(UserId(0), "name02", 0, 0))
        some <- userRepo.get(created01.id)
        none <- userRepo.get(UserId(created01.id.value + 1))
      } yield {
        some === Some(created01)
        none === None
      }

      Await.result(asyncTest, 10.seconds)
    }
  }
}

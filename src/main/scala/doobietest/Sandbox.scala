package doobietest

import java.sql.Connection

import cats.data.Kleisli
import doobie._
import doobie.implicits._
import cats.effect.{Blocker, IO}

import scala.concurrent.ExecutionContext

object Sandbox {

  implicit val cs = IO.contextShift(ExecutionContext.global)

  val xa = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:world",
    "postgres",
    ""
  )

  case class Country(code: String, name: String, population: Long)

  def find(n: String): ConnectionIO[Option[Country]] =
    sql"select code, name, population from country where name = $n".query[Country].option

  val a: IO[Option[Country]] = find("France").transact(xa)

  val b: Option[Country] = find("France").transact(xa).unsafeRunSync

}

package dbcontext

import java.sql.Connection

import cats.data.Kleisli
import cats.effect.{Blocker, IO}
import doobie.{ConnectionIO, Transactor}
import monix.eval.Task

import scala.concurrent.ExecutionContext

object JdbcIO {
  def apply[A](a: => A): JdbcIO[A] = Kleisli(_ => Task(a))

  def withConnection[A](f: Connection => A): JdbcIO[A] = Kleisli({ c: Connection => Task(f(c)) })

  def withAnorm[A](f: Connection => A): JdbcIO[A] = withConnection(f)

  def withScalikeJdbc[A](f: scalikejdbc.DBSession => A): JdbcIO[A] = withConnection { c: Connection =>
    f(scalikejdbc.DBSession(c, tx = Some(new scalikejdbc.Tx(c))))
  }

  def withConnectionIO[A](cio: ConnectionIO[A])(implicit ec: ExecutionContext): JdbcIO[A] = {
    import doobie.implicits._
    implicit val cs = IO.contextShift(ec)
    val blocker     = Blocker.liftExecutionContext(ec)
    withConnection({ c: Connection =>
      val transactor: Transactor[IO] = Transactor.fromConnection(c, blocker)
      cio.transact(transactor).unsafeRunSync
    })
  }
}

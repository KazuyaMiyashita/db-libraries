package dbcontext

import java.sql.Connection

import cats.data.Kleisli
import monix.eval.Task

object JdbcIO {
  def apply[A](a: => A): JdbcIO[A] = Kleisli(_ => Task(a))

  def withConnection[A](f: Connection => Task[A]): JdbcIO[A] = Kleisli(f)

  def withAnorm[A](f: Connection => A): JdbcIO[A] = withConnection { c: Connection => Task(f(c)) }

  def withScalikeJdbc[A](f: scalikejdbc.DBSession => A): JdbcIO[A] = withConnection { c: Connection =>
    Task(f(scalikejdbc.DBSession(c)))
  }
}

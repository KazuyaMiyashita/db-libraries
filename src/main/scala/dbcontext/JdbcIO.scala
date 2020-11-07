package dbcontext

import java.sql.Connection

import cats.data.Kleisli
import monix.eval.Task

object JdbcIO {
  def apply[A](a: => A): JdbcIO[A] = Kleisli(_ => Task(a))

  def withConnection[A](f: Connection => A): JdbcIO[A] = Kleisli({ c: Connection => Task(f(c)) })

  def withAnorm[A](f: Connection => A): JdbcIO[A] = withConnection(f)

  def withScalikeJdbc[A](f: scalikejdbc.DBSession => A): JdbcIO[A] = withConnection { c: Connection =>
    f(scalikejdbc.DBSession(c, tx = Some(new scalikejdbc.Tx(c))))
  }
}

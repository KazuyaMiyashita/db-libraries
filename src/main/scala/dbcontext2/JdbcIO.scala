package dbcontext2

import java.sql.Connection

import cats.data.Kleisli
import cats.effect.{Blocker, IO}
import doobie.{ConnectionIO, KleisliInterpreter}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.{JdbcBackend, JdbcDataSource}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object JdbcIO {

  def apply[A](a: => A): JdbcIO[A] = Kleisli(_ => IO(a))

  def withConnection[A](f: Connection => A): JdbcIO[A] =
    Kleisli({ case (c, _) => IO(f(c)) })

  def withAnorm[A](f: Connection => A): JdbcIO[A] = withConnection(f)

  def withScalikeJdbc[A](f: scalikejdbc.DBSession => A): JdbcIO[A] = withConnection { c: Connection =>
    f(scalikejdbc.DBSession(c, tx = Some(new scalikejdbc.Tx(c))))
  }

  def withDoobie[A](cio: ConnectionIO[A]): JdbcIO[A] = {
    Kleisli {
      case (c, ec) =>
        implicit val cs                         = IO.contextShift(ec)
        val blocker                             = Blocker.liftExecutionContext(ec)
        val interpreter                         = KleisliInterpreter[IO](blocker).ConnectionInterpreter
        val kleisli: Kleisli[IO, Connection, A] = cio.foldMap(interpreter)
        kleisli.run(c)
    }
  }

  def withSlick[A](
      io: slick.dbio.DBIOAction[A, slick.dbio.NoStream, slick.dbio.Effect.All]
  )(implicit ec: ExecutionContext): JdbcIO[A] =
    Kleisli {
      case (c, ec) =>
        implicit val cs = IO.contextShift(ec)
        val dummySource: JdbcDataSource = new JdbcDataSource {
          override def createConnection(): Connection = c
          override def close(): Unit                  = ()
          override val maxConnections: Option[Int]    = None
        }
        val asyncExecutor: AsyncExecutor = new AsyncExecutor {
          override def executionContext: ExecutionContext = ec
          override def close(): Unit                      = ()
        }
        val db: JdbcBackend.Database = Database.forSource(dummySource, asyncExecutor)
        IO.fromFuture(IO(db.run(io)))
    }

}

package dbcontext2

import java.sql.Connection

import cats.data.Kleisli
import cats.effect.{Blocker, IO}
import doobie.{ConnectionIO, Transactor}
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.{DataSourceJdbcDataSource, JdbcBackend, JdbcDataSource}
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object JdbcIO {
  def apply[A](a: => A): JdbcIO[A] = Kleisli(_ => IO(a))

  def withConnection[A](f: Connection => A): JdbcIO[A] =
    Kleisli({ case (ds, _) => IO(f(ds.getConnection())) })

  def withAnorm[A](f: Connection => A): JdbcIO[A] = withConnection(f)

  def withScalikeJdbc[A](f: scalikejdbc.DBSession => A): JdbcIO[A] = withConnection { c: Connection =>
    f(scalikejdbc.DBSession(c, tx = Some(new scalikejdbc.Tx(c))))
  }

  def withDoobie[A](cio: ConnectionIO[A]): JdbcIO[A] = {
    import doobie.implicits._
    Kleisli {
      case (ds, ec) =>
        implicit val cs                = IO.contextShift(ec)
        val blocker                    = Blocker.liftExecutionContext(ec)
        val transactor: Transactor[IO] = Transactor.fromConnection(ds.getConnection(), blocker)
        cio.transact(transactor)
    }
  }

  def withSlick[A](
      io: slick.dbio.DBIOAction[A, slick.dbio.NoStream, slick.dbio.Effect.All]
  )(implicit ec: ExecutionContext): JdbcIO[A] =
    Kleisli {
      case (ds, ec) =>
        implicit val cs = IO.contextShift(ec)
        val source: JdbcDataSource =
          new DataSourceJdbcDataSource(ds, keepAliveConnection = false, maxConnections = None)
        val asyncExecutor: AsyncExecutor = new AsyncExecutor {
          override def executionContext: ExecutionContext = ec
          override def close(): Unit                      = ()
        }
        val db: JdbcBackend.Database = Database.forSource(source, asyncExecutor)
        IO.fromFuture(IO(db.run(io)))
    }

}

package dbcontext2

import cats.effect.IO
import javax.sql.DataSource

import scala.concurrent.{ExecutionContext, Future}

trait JdbcIORunner {
  def runTx[A](io: JdbcIO[A]): IO[A]
  final def runTxToFuture[A](io: JdbcIO[A]): Future[A] = runTx(io).unsafeToFuture()
}

class DefaultJdbcIORunner(dataSource: DataSource, ec: ExecutionContext) extends JdbcIORunner {

  override def runTx[A](io: JdbcIO[A]): IO[A] = {
    for {
      connection <- IO(dataSource.getConnection())
      _          <- IO(connection.setAutoCommit(false))
      result <- io
        .run((dataSource, ec))
        .redeemWith(
          { e => connection.rollback(); IO.raiseError(e) }, { other: A => connection.commit(); IO(other) }
        )
        .guarantee(IO(connection.close()))
    } yield result
  }

}

class TestRollbackJdbcIORunner(dataSource: DataSource, ec: ExecutionContext) extends JdbcIORunner {

  override def runTx[A](io: JdbcIO[A]): IO[A] = {
    for {
      connection <- IO(dataSource.getConnection())
      _          <- IO(connection.setAutoCommit(false))
      result <- io
        .run((dataSource, ec))
        .redeemWith(
          { e => connection.rollback(); IO.raiseError(e) }, { other: A => connection.rollback(); IO(other) }
        )
        .guarantee(IO(connection.close()))
    } yield result
  }

}

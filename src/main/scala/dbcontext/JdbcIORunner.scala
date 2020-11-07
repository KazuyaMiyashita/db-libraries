package dbcontext

import monix.eval.Task
import monix.execution.Scheduler

import scala.concurrent.Future

trait JdbcIORunner {
  def run[A](io: JdbcIO[A]): Task[A]
  def runToFuture[A](io: JdbcIO[A]): Future[A]
}

class DefaultJdbcIORunner(dbPool: JdbcConnectionPool)(implicit scheduler: Scheduler) extends JdbcIORunner {

  override def run[A](io: JdbcIO[A]): Task[A] = {
    for {
      connection <- Task(dbPool.getConnection())
      _          <- Task(connection.setAutoCommit(false))
      result <- io
        .run(connection)
        .redeemWith(
          { e => connection.rollback(); Task.raiseError(e) }, { other: A => connection.commit(); Task(other) }
        )
        .guarantee(Task.eval(connection.close()))
    } yield result
  }

  override def runToFuture[A](io: JdbcIO[A]): Future[A] = run(io).runToFuture

}

class TestRollbackJdbcIORunner(dbPool: JdbcConnectionPool)(implicit scheduler: Scheduler) extends JdbcIORunner {

  override def run[A](io: JdbcIO[A]): Task[A] = {
    for {
      connection <- Task(dbPool.getConnection())
      _          <- Task(connection.setAutoCommit(false))
      result <- io
        .run(connection)
        .redeemWith(
          { e => connection.rollback(); Task.raiseError(e) }, { other: A => connection.rollback(); Task(other) }
        )
        .guarantee(Task.eval(connection.close()))
    } yield result
  }

  override def runToFuture[A](io: JdbcIO[A]): Future[A] = run(io).runToFuture

}

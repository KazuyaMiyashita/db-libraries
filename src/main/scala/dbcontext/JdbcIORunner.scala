package dbcontext

import monix.eval.Task
import monix.execution.Scheduler

trait JdbcIORunner {
  def run[A](io: JdbcIO[A]): Task[A]
}

class DefaultJdbcIORunner(dbPool: JdbcConnectionPool)(implicit scheduler: Scheduler) extends JdbcIORunner {

  override def run[A](io: JdbcIO[A]): Task[A] = {
    for {
      connection <- dbPool.getConnection()
      result <- io
        .run(connection)
        .redeemWith(
          { e => connection.rollback(); Task.raiseError(e) }, { other: A => connection.commit(); Task.apply(other) }
        )
        .guarantee(Task.eval(connection.close()))
    } yield result
  }

}

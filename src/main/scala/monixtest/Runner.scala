package monixtest

import monix.eval.Task

object Runner {

  def run[A](io: IO[A]): Task[A] = {
    val connection = new DummyConnection("connection2")
    io.run(connection)
      .redeemWith(
        { e => connection.rollback(); Task.raiseError(e) }, { other: A => connection.commit(); Task.apply(other) }
      )
      .guarantee(Task.eval(connection.close()))
  }

  def commitWhen[A, B](io: IO[A])(cond: A => Either[B, A]): Task[Either[B, A]] = {
    val connection = new DummyConnection("connection2")
    io.run(connection)
      .redeemWith(
        { e => connection.rollback(); Task.raiseError(e) }, { result =>
          cond(result) match {
            case Right(v) => connection.commit(); Task.apply(Right(v))
            case Left(a)  => connection.rollback(); Task.apply(Left(a))
          }
        }
      )
      .guarantee(Task.eval(connection.close()))
  }

}

package monixtest

import monix.eval.Task

case class Runner(connectionName: String) {

  def run[A](io: IO[A]): Task[A] = {

    io match {
      case PureIO(value) => Task.apply(value)
      case ImpureIO(run) => {
        val connection = new DummyConnection(connectionName)
        run(connection)
          .redeemWith(
            { e => connection.rollback(); Task.raiseError(e) }, { other: A => connection.commit(); Task.apply(other) }
          )
          .guarantee(Task.eval(connection.close()))
      }
    }
  }

  def commitWhen[A, B](io: IO[A])(cond: A => Either[B, A]): Task[Either[B, A]] = {
    val connection = new DummyConnection(connectionName)
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

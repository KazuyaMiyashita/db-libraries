package monixtest

import cats.data.Kleisli
import monix.eval.Task

object IO {

  def apply[A](a: => A): IO[A] = Kleisli(_ => Task(a))
//  def pure[A](a: A): IO[A] = Kleisli(_ => Task.pure(a))

  def withConnection[A](f: DummyConnection => Task[A]): IO[A] = Kleisli(f)

}

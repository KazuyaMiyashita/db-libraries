package monixtest

import cats.{Monad, MonadError}
import cats.data.Kleisli
import monix.eval.Task

sealed trait IO[A] {
  def run: DummyConnection => Task[A]
  def map[B](f: A => B): IO[B]         = IO.monadInstance.map(this)(f)
  def flatMap[B](f: A => IO[B]): IO[B] = IO.monadInstance.flatMap(this)(f)
}
private[monixtest] final case class PureIO[A](value: A) extends IO[A] {
  override def run: DummyConnection => Task[A] = _ => Task.apply(value)
}
private[monixtest] final case class ImpureIO[A](run: DummyConnection => Task[A]) extends IO[A]

object IO {

//  implicit def monadInstance[A](io: IO[A]): Monad[IO[A]] = Kleisli(a => io.run(a))

  implicit def monadInstance: Monad[IO] = new Monad[IO] {
    override def pure[A](x: A): IO[A] = IO.pure(x)

    override def flatMap[A, B](fa: IO[A])(f: A => IO[B]): IO[B] = {
      fa match {
        case PureIO(value) =>
          f(value) match {
            case v: PureIO[B]     => v
            case run: ImpureIO[B] => run
          }
        case ImpureIO(run) => ImpureIO { c => run(c).flatMap { a => f(a).run(c) } }
      }
    }

    override def tailRecM[A, B](a: A)(f: A => IO[Either[A, B]]): IO[B] = ???
  }

  def apply[A](a: => A): IO[A] = PureIO[A](a)

  def pure[A](a: A): IO[A] = PureIO[A](a)

  def withConnection[A](f: DummyConnection => Task[A]): IO[A] = ImpureIO(f)

}

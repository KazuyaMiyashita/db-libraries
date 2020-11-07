import cats.data.Kleisli
import monix.eval.Task

package object monixtest {

  type IO[A] = Kleisli[Task, DummyConnection, A]

}

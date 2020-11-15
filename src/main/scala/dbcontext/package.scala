import cats.data.Kleisli
import monix.eval.Task
import java.sql.Connection

package object dbcontext {

  type JdbcIO[A] = Kleisli[Task, Connection, A]

}

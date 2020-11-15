import java.sql.Connection
import cats.data.Kleisli
import cats.effect.IO

import scala.concurrent.ExecutionContext

package object dbcontext2 {

  type JdbcIO[A] = Kleisli[IO, (Connection, ExecutionContext), A]

}

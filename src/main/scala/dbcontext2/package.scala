import cats.data.Kleisli
import cats.effect.IO
import javax.sql.DataSource

import scala.concurrent.ExecutionContext

package object dbcontext2 {

  type JdbcIO[A] = Kleisli[IO, (DataSource, ExecutionContext), A]

}

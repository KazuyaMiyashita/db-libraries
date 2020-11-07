import cats.data.Kleisli
import monix.eval.Task
import java.sql.Connection

package object dbcontext {

  type JdbcIO[A] = Kleisli[Task, Connection, A]

}

object Sandbox {

  import dbcontext._

  import anorm._

  val anormIO: JdbcIO[List[String]] = JdbcIO.withAnorm { implicit c =>
    SQL("select a from As").as(SqlParser.str("a").*)
  }

  import scalikejdbc._

  import dbcontext.JdbcIO
  val scalikejdbcIO: JdbcIO[Option[String]] = JdbcIO.withScalikeJdbc { implicit s =>
    sql"select name from emp where id = 42".map(rs => rs.string("name")).single.apply()
  }

  val io: JdbcIO[(List[String], Option[String])] = for {
    a <- anormIO
    s <- scalikejdbcIO
  } yield (a, s)

  val runner: JdbcIORunner                    = ???
  val a: Task[(List[String], Option[String])] = runner.run(io)

}

package dbcontext

import java.sql.Connection

import monix.eval.Task

trait JdbcConnectionPool {
  def getConnection(): Task[Connection]
}

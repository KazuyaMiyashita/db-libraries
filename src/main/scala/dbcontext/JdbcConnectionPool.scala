package dbcontext

import java.sql.Connection

trait JdbcConnectionPool {
  def getConnection(): Connection
}

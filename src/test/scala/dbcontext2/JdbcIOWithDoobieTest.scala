package dbcontext2

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.concurrent.Executors

import javax.sql.DataSource
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito._
import org.scalatest.flatspec.AsyncFlatSpec
import doobie.implicits._

import scala.concurrent.ExecutionContext

class JdbcIOWithDoobieTest extends AsyncFlatSpec {

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  "JdbcIO.withDoobie" should "have no effects for Connection" in {

    val mockResultSet: ResultSet = mock(classOf[ResultSet])
    when(mockResultSet.next()).thenReturn(false)

    val mockPreparedStatement: PreparedStatement = mock(classOf[PreparedStatement])
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet)

    val mockConnection: Connection = mock(classOf[Connection])
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement)

    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    val io: JdbcIO[Option[Int]] = JdbcIO.withDoobie {
      sql"select * from Doobie".query[Int].option
    }
    val runner = new DefaultJdbcIORunner(mockDataSource, ec)

    runner.runTx(io).unsafeToFuture().map { _ =>
      /* Doobie */

      /**
        * resultSetのnext()は1回ではなく2回呼ばれる
        *
        * Equivalent to `getNext`, but verifies that there is at most one row remaining.
        * throws `UnexpectedContinuation` if there is more than one row remaining
        *
        * https://github.com/tpolecat/doobie/blob/v0.9.2/modules/core/src/main/scala/doobie/hi/resultset.scala#L215
        */
      verify(mockResultSet, times(2)).next()
      verify(mockPreparedStatement, times(1)).executeQuery()
      verify(mockConnection, times(1)).prepareStatement("select * from Doobie")

      verify(mockConnection, never).abort(any())
      verify(mockConnection, never).clearWarnings()
      verify(mockConnection, never).releaseSavepoint(any())
      verify(mockConnection, never).rollback()
      verify(mockConnection, never).rollback(any())
      verify(mockConnection, never).setAutoCommit(true)
      verify(mockConnection, never).setReadOnly(any())
      verify(mockConnection, never).setTransactionIsolation(any())

      /* JdbcIORunner */
      verify(mockConnection, times(1)).setAutoCommit(false)
      verify(mockConnection, times(1)).close()
      verify(mockConnection, times(1)).commit()

      succeed
    }

  }

}

package dbcontext2

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.concurrent.Executors

import javax.sql.DataSource
import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito._
import org.scalatest.flatspec.AsyncFlatSpec

import scala.concurrent.ExecutionContext

class JdbcIOWithSlickTest extends AsyncFlatSpec {

  implicit val ec: ExecutionContext = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(2))

  "JdbcIO.withSlick" should "have no effects for Connection" in {

    val mockResultSet: ResultSet = mock(classOf[ResultSet])
    when(mockResultSet.next()).thenReturn(false)

    val mockPreparedStatement: PreparedStatement = mock(classOf[PreparedStatement])
    when(mockPreparedStatement.executeQuery()).thenReturn(mockResultSet)

    val mockConnection: Connection = mock(classOf[Connection])
    when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement)

    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    val io: JdbcIO[Option[Int]] = JdbcIO.withSlick {
      import slick.jdbc.MySQLProfile.api._
      sql"select * from Slick".as[Int].headOption
    }
    val runner = new DefaultJdbcIORunner(mockDataSource, ec)

    runner.runTx(io).unsafeToFuture().map { _ =>
      /* Slick */
      verify(mockResultSet, times(1)).next()
      verify(mockPreparedStatement, times(1)).executeQuery()
      verify(mockConnection, times(1)).prepareStatement(anyString())

      verify(mockConnection, never).abort(any())
      verify(mockConnection, never).clearWarnings()
      verify(mockConnection, never).releaseSavepoint(any())
      verify(mockConnection, never).rollback()
      verify(mockConnection, never).rollback(any())
      verify(mockConnection, never).setReadOnly(any())
      verify(mockConnection, never).setTransactionIsolation(any())

      /* JdbcIORunner */
      verify(mockConnection, times(1)).setAutoCommit(any())
      verify(mockConnection, times(1)).close()
      verify(mockConnection, times(1)).commit()

      succeed
    }

  }

}

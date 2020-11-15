package dbcontext2

import java.sql.Connection
import java.util.concurrent.Executors

import cats.Monad
import javax.sql.DataSource
import org.scalatest.flatspec.AsyncFlatSpec
import org.mockito.Mockito._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class JdbcIOTest extends AsyncFlatSpec {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

  "DefaultJdbcIORunner#runTx" should "setAutoCommit(false) is called only once, setAutoCommit(true) is never called" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    val io: JdbcIO[Int] = JdbcIO(42)
    val runner          = new DefaultJdbcIORunner(mockDataSource, ec)

    runner.runTx(io).unsafeToFuture().map { result =>
      verify(mockConnection, times(1)).setAutoCommit(false)
      verify(mockConnection, never).setAutoCommit(true)
      assert(result == 42)
    }
  }

  "DefaultJdbcIORunner#runTx" should "when there are no exceptions, commit() and close() is called only once, rollback() is never called" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    val io: JdbcIO[Int] = JdbcIO(42)
    val runner          = new DefaultJdbcIORunner(mockDataSource, ec)

    runner.runTx(io).unsafeToFuture().map { result =>
      verify(mockConnection, times(1)).commit()
      verify(mockConnection, times(1)).close()
      verify(mockConnection, never).rollback()
      assert(result == 42)
    }
  }

  "DefaultJdbcIORunner#runTx" should "when there are exceptions, rollback() and close() is called only once, commit() is never called" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    val io: JdbcIO[Int] = JdbcIO(throw new RuntimeException("exception I just threw"))
    val runner          = new DefaultJdbcIORunner(mockDataSource, ec)

    runner.runTx(io).unsafeToFuture().transformWith {
      case Success(_) => fail()
      case Failure(e) => {
        verify(mockConnection, times(1)).rollback()
        verify(mockConnection, times(1)).close()
        verify(mockConnection, never).commit()
        assert(e.getMessage == "exception I just threw")
      }
    }
  }

  "JdbcIO.apply" should "have no side effects until runner runs" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    class SideEffect {
      def execute(): Unit = ()
    }
    val mockSideEffect: SideEffect = mock(classOf[SideEffect])

    JdbcIO({ mockSideEffect.execute(); 42 })

    verify(mockSideEffect, never).execute()
    succeed
  }

  "JdbcIO.apply" should "have no side effects during composition of for expression" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    class SideEffect {
      def execute(): Unit = ()
    }
    val mockSideEffect: SideEffect = mock(classOf[SideEffect])

    for {
      _ <- JdbcIO({ mockSideEffect.execute(); 42 })
      _ <- JdbcIO({ mockSideEffect.execute(); 42 })
    } yield ()

    verify(mockSideEffect, never).execute()
    succeed
  }

  "JdbcIO.apply" should "has side effects when runner runs" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    class SideEffect {
      def execute(): Unit = ()
    }
    val mockSideEffect: SideEffect = mock(classOf[SideEffect])

    val io = for {
      _ <- JdbcIO({ mockSideEffect.execute(); 42 })
      _ <- JdbcIO({ mockSideEffect.execute(); 42 })
    } yield ()
    val runner = new DefaultJdbcIORunner(mockDataSource, ec)
    runner.runTx(io).unsafeToFuture().map { _ =>
      verify(mockSideEffect, times(2)).execute()
      succeed
    }

  }

  "Monad[JdbcIO].pure" should "has side effects" in {

    val mockConnection: Connection = mock(classOf[Connection])
    val mockDataSource: DataSource = mock(classOf[DataSource])
    when(mockDataSource.getConnection()).thenReturn(mockConnection)

    class SideEffect {
      def execute(): Unit = ()
    }
    val mockSideEffect: SideEffect = mock(classOf[SideEffect])

    Monad[JdbcIO].pure({ mockSideEffect.execute(); 42 })

    verify(mockSideEffect, times(1)).execute()
    succeed
  }

}

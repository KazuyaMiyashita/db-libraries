package dbcontext

import java.sql.{Connection, DriverManager}
import java.util.UUID

import monix.eval.Task
import monix.execution.Scheduler
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import scalikejdbc.WrappedResultSet

import scala.concurrent.Await
import scala.concurrent.duration._

class JdbcIORunnerSpec extends AsyncFlatSpec with BeforeAndAfterAll {

  implicit val scheduler = Scheduler.fixedPool("scheduler", 4)

  val jdbcConnectionPool = new JdbcConnectionPool {
    Class.forName("com.mysql.cj.jdbc.Driver")
    override def getConnection(): Task[Connection] = {
      Task.apply(
        DriverManager.getConnection(
          "jdbc:mysql://localhost:3306/db",
          "root",
          ""
        )
      )
    }
  }

  val runner: JdbcIORunner = new DefaultJdbcIORunner(jdbcConnectionPool)(scheduler)

  override def beforeAll() {
    Await.result(
      jdbcConnectionPool.getConnection.map { c =>
        val p = c.prepareStatement("create table products (id varchar(45), name varchar(255), price int);")
        p.executeUpdate()
      }.runToFuture,
      10.seconds
    )
  }

  override def afterAll(): Unit = {
    Await.result(
      jdbcConnectionPool.getConnection.map { c =>
        val p = c.prepareStatement("drop table products;")
        p.executeUpdate()
      }.runToFuture,
      10.seconds
    )
  }

  case class Product(id: UUID, name: String, price: Int)

  it should "select" in {

    import anorm.{SQL, RowParser, SqlParser}
    import scalikejdbc._

    val anormProductParser: RowParser[Product] = for {
      id    <- SqlParser.str("id")
      name  <- SqlParser.str("name")
      price <- SqlParser.int("price")
    } yield Product(UUID.fromString(id), name, price)

    def scalikeJdbcParser(rs: WrappedResultSet): Product = Product(
      UUID.fromString(rs.string("id")),
      rs.string("name"),
      rs.int("price")
    )

    val products = (1 to 100).map { i => Product(UUID.randomUUID(), s"product$i", i * 100) }

    val io: JdbcIO[(Product, Product, Product)] = for {
      _ <- JdbcIO.withConnection { c =>
        val s = c.createStatement()
        products.foreach(p => s.addBatch(s"insert into products values ('${p.id.toString}', '${p.name}', ${p.price})"))
        s.executeBatch()
      }
      p1 <- JdbcIO.withAnorm { implicit c =>
        SQL("select * from products where price = 5000;").as(anormProductParser.single)
      }
      p2 <- JdbcIO.withScalikeJdbc { implicit s =>
        sql"select * from products where price = 5000".map(scalikeJdbcParser).single.apply().get
      }
      p3 <- JdbcIO.withAnorm { implicit c =>
        SQL("select * from products where price = 5000;").as(anormProductParser.single)
      }
    } yield (p1, p2, p3)

    runner
      .runToFuture(io)
      .map {
        case (p1, p2, p3) =>
          assert(p1.name == "product50")
          assert(p1.price == 5000)
          assert(p1 == p2)
          assert(p2 == p3)
      }

  }

}

package dbcontext

import java.sql.{Connection, DriverManager}
import java.util.UUID

import monix.execution.Scheduler
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec

class JdbcIORunnerSpec extends AsyncFlatSpec with BeforeAndAfterAll {

  implicit val scheduler = Scheduler.fixedPool("scheduler", 4)

  val jdbcConnectionPool = new JdbcConnectionPool {
    Class.forName("com.mysql.cj.jdbc.Driver")
    override def getConnection(): Connection = {
      DriverManager.getConnection(
        "jdbc:mysql://localhost:3306/db",
        "root",
        ""
      )
    }
  }

  override def beforeAll() {
    val c = jdbcConnectionPool.getConnection
    val p = c.prepareStatement("create table products (id varchar(45), name varchar(255), price int);")
    p.executeUpdate()
  }

  override def afterAll(): Unit = {
    val c = jdbcConnectionPool.getConnection
    val p = c.prepareStatement("drop table products;")
    p.executeUpdate()
  }

  case class Product(id: UUID, name: String, price: Int)
  val products = (1 to 100).map { i => Product(UUID.randomUUID(), s"product$i", i * 100) }

  import anorm.{SQL, RowParser, SqlParser}
  import scalikejdbc._

  val anormProductParser: RowParser[Product] = for {
    id    <- SqlParser.str("id")
    name  <- SqlParser.str("name")
    price <- SqlParser.int("price")
  } yield Product(UUID.fromString(id), name, price)

  def scalikejdbcParser(rs: WrappedResultSet): Product = Product(
    UUID.fromString(rs.string("id")),
    rs.string("name"),
    rs.int("price")
  )

  val insertIO: JdbcIO[Unit] = JdbcIO.withConnection { c =>
    val s = c.createStatement()
    products.foreach(p => s.addBatch(s"insert into products values ('${p.id.toString}', '${p.name}', ${p.price})"))
    s.executeBatch()
  }

  val anormSelectIO: JdbcIO[List[Product]] = JdbcIO.withAnorm { implicit c =>
    SQL("select * from products").as(anormProductParser.*)
  }
  val scalikejdbcSelectIO: JdbcIO[List[Product]] = JdbcIO.withScalikeJdbc { implicit s =>
    sql"select * from products".map(scalikejdbcParser).list.apply()
  }

  it should "select" in {

    val runner: JdbcIORunner = new TestRollbackJdbcIORunner(jdbcConnectionPool)(scheduler)

    val io: JdbcIO[(List[Product], List[Product], List[Product])] = for {
      _   <- insertIO
      ps1 <- anormSelectIO
      ps2 <- scalikejdbcSelectIO
      ps3 <- anormSelectIO
    } yield (ps1, ps2, ps3)

    val io2: JdbcIO[List[Product]] = JdbcIO.withScalikeJdbc { implicit s =>
      sql"select * from products".map(scalikejdbcParser).list.apply()
    }

    runner
      .runToFuture(io)
      .map {
        case (ps1, ps2, ps3) =>
          assert(ps1 == products)
          assert(ps2 == products)
          assert(ps3 == products)
      }

    for {
      res1 <- runner.runToFuture(io)
      res2 <- runner.runToFuture(io2)
    } yield {
      assert(res1._1 == products)
      assert(res1._2 == products)
      assert(res1._3 == products)
      assert(res2 == Nil)
    }

  }

}

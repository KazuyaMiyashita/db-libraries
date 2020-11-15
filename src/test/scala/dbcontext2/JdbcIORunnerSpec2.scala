package dbcontext2

import java.util.UUID
import java.util.concurrent.Executors

import javax.sql.DataSource
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec
import org.h2.jdbcx.JdbcDataSource
import org.h2.tools.DeleteDbFiles

import scala.concurrent.ExecutionContext

class JdbcIORunnerSpec2 extends AsyncFlatSpec with BeforeAndAfterAll {

  implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(4))

  val dataSource: DataSource = {
    val ds = new JdbcDataSource()
    ds.setUrl("jdbc:h2:./test2")
    ds.setUser("sa")
    ds.setPassword("")
    ds
  }

  override def beforeAll() {
    val c = dataSource.getConnection()
    val p = c.prepareStatement("create table products (id varchar(45), name varchar(255), price int);")
    p.executeUpdate()
  }

  override def afterAll(): Unit = {
    val c = dataSource.getConnection()
    val p = c.prepareStatement("drop table products;")
    p.executeUpdate()

    DeleteDbFiles.execute("./", "test2", true)
  }

  case class Product(id: UUID, name: String, price: Int)
  val products = (1 to 100).map { i => Product(UUID.randomUUID(), s"product$i", i * 100) }

  import anorm.{RowParser, SQL, SqlParser}
  import scalikejdbc.WrappedResultSet

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
    import scalikejdbc._
    sql"select * from products".map(scalikejdbcParser).list.apply()
  }

  val doobieSelectIO: JdbcIO[List[Product]] = JdbcIO.withDoobie {
    import doobie._
    import doobie.implicits._
//    implicit val stringUUIDRead: Read[UUID] = Read[String].map(UUID.fromString)
    case class Pr(id: String, name: String, price: Int)
    val prsIO: doobie.ConnectionIO[List[Pr]] = sql"select * from products".query[Pr].to[List]
    prsIO.map { prs =>
      prs.map {
        case Pr(id, name, price) => Product(UUID.fromString(id), name, price)
      }
    }
  }

  it should "select" in {

    val runner: JdbcIORunner = new TestRollbackJdbcIORunner(dataSource, ec)

    val io: JdbcIO[(List[Product], List[Product], List[Product])] = for {
      _   <- insertIO
      ps1 <- anormSelectIO
      ps2 <- scalikejdbcSelectIO
//      ps3 <- doobieSelectIO
      ps3 <- JdbcIO.apply(Nil)
    } yield (ps1, ps2, ps3)

    val io2: JdbcIO[List[Product]] = JdbcIO.withScalikeJdbc { implicit s =>
      import scalikejdbc._
      sql"select * from products".map(scalikejdbcParser).list.apply()
    }

    runner
      .runTxToFuture(io)
      .map {
        case (ps1, ps2, ps3) =>
          assert(ps1 == products)
          assert(ps2 == products)
//          assert(ps3 == products)
      }

    for {
      res1 <- runner.runTxToFuture(io)
      res2 <- runner.runTxToFuture(io2)
    } yield {
      assert(res1._1 == products)
      assert(res1._2 == products)
//      assert(res1._3 == products)
      assert(res2 == Nil)
    }

  }

}

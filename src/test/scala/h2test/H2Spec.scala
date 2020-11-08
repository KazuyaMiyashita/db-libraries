package h2test

import java.sql.DriverManager
import java.util.UUID

import org.scalatest.flatspec.AnyFlatSpec

class H2Spec extends AnyFlatSpec {

  case class Product(id: UUID, name: String, price: Int)
  val products = (1 to 100).map { i => Product(UUID.randomUUID(), s"product$i", i * 100) }

  it should "create database" in {

    Class.forName("org.h2.Driver")
    val c = DriverManager.getConnection(
      "jdbc:h2:~/test",
      "sa",
      ""
    )

    c.setAutoCommit(true)

    c.prepareStatement("create table products (id varchar(45), name varchar(255), price int);")
      .executeUpdate()

    val s = c.createStatement()
    products.foreach(p => s.addBatch(s"insert into products values ('${p.id.toString}', '${p.name}', ${p.price})"))
    s.executeBatch()

    val resultSet = c.prepareStatement("select * from products").executeQuery()

    val result: List[Product] = Iterator
      .continually(resultSet)
      .takeWhile(_.next())
      .map { row => Product(UUID.fromString(row.getString("id")), row.getString("name"), row.getInt("price")) }
      .toList

    c.prepareStatement("drop table products;")
      .executeUpdate()

    c.close()

    assert(result == products)

  }

}

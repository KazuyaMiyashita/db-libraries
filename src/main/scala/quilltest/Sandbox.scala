package quilltest

import java.io.Closeable
import java.sql.Connection

import cats.data.Kleisli
import io.getquill.{NamingStrategy, _}
import io.getquill.monad.Effect
import javax.sql.DataSource
import quilltest.Sandbox.ctx
import dbcontext2.JdbcIO
import monix.eval.Task
import cats.effect.{IO => CatsIO}

object Sandbox {

  case class Person(name: String, age: Int)

  val ctx = new MysqlJdbcContext(SnakeCase, "ctx")
  import ctx._

  val q: ctx.Quoted[EntityQuery[String]] = quote {
    query[Person].filter(p => p.name == "John").map(p => p.name)
  }

  case class Product(id: Int, description: String, sku: Long)

  val q2: ctx.Quoted[ActionReturning[Product, Index]] = quote {
    query[Product].insert(lift(Product(0, "My Product", 1011L))).returningGenerated(_.id)
  }
//
//  val returnedIds: ctx.IO[ctx.Index, Effect.Write] = ctx.runIO(q2)
//
//  val r = ctx.withConnection()

  val datasource: DataSource with Closeable = ???
  val ctx2                                  = new MysqlJdbcContext(SnakeCase, datasource)

  def withQuillMysql[A, N <: NamingStrategy](namingStrategy: N)(f: MysqlJdbcContext[N] => A): JdbcIO[A] =
    Kleisli {
      case (c, ec) =>
        val ctx = new MysqlJdbcContext(namingStrategy, datasource)
        CatsIO(ctx.transaction(f(ctx)))
    }

}

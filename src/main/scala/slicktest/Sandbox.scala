package slicktest

import java.sql.Connection

import cats.data.Kleisli
import dbcontext.JdbcIO
import monix.eval.Task
import slick.jdbc.{JdbcBackend, JdbcDataSource}
import slick.jdbc.JdbcBackend.Database
import slick.util.AsyncExecutor

import scala.concurrent.ExecutionContext

object Sandbox {}

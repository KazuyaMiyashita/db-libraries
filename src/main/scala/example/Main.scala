package example

import monix.eval.Task
import monix.execution.Scheduler
import monixtest._

object Main extends App {

  implicit val scheduler = Scheduler.fixedPool("scheduler", 4)

  val io1: IO[Int] = IO { println("io1: side effect in connection"); 42 }
//  val io2: IO[Int] = IO.pure { println("io2: side effect before connection begin"); 42 }

  val io3: IO[String] = IO.withConnection { c => println("io3"); Task("io3") }
  val io4: IO[String] = IO.withConnection { c => println("io4"); throw new Exception("boo"); Task("io4") }
  val io5: IO[String] = IO.withConnection { c => println("io5"); Task("io5") }

  val io: IO[String] = for {
    _ <- io1
//    _ <- io2
    _  <- io3
    _  <- io4
    a5 <- io5
  } yield a5

  Runner.run(io1).runAsync { res => println(s"result: $res") }

//  Runner.commitWhen(io3) { res => Either.cond(res == "io2", "ok", "bad") }.runAsync { res => println(s"result: $res") }

}

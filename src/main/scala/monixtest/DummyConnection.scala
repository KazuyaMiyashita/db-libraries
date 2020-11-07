package monixtest

class DummyConnection(name: String) {

  println(s"connection $name opened")

  def commit(): Unit = {
//    throw new Exception("commit error")
    println(s"connection $name committed")
  }
  def rollback(): Unit = {
//    throw new Exception("rollback error")
    println(s"connection $name rolled back")
  }
  def close(): Unit = {
//    throw new Exception("close error")
    println(s"connection $name closed")
  }

}

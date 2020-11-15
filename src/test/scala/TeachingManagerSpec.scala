import monix.execution.Scheduler
import org.scalatest.BeforeAndAfterAll
import org.scalatest.flatspec.AsyncFlatSpec

class TeachingManagerSpec extends AsyncFlatSpec with BeforeAndAfterAll {

  implicit val scheduler = Scheduler.fixedPool("scheduler", 4)

}

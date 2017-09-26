package utils

import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import org.slf4j.LoggerFactory

import scala.collection.immutable._
import scala.language.postfixOps
import scala.sys.process._
import scala.util.{Success, Try}

object LocalPubSub {

  private val StartedExpression = """.*INFO: Server started, listening on (\d{4,5})""".r

  case class PubSubEnvironment(host: String, port: Int)
}

trait LocalPubSub extends BeforeAndAfterAll with Eventually with Matchers {
  this: Suite =>

  import LocalPubSub._

  private val logger = LoggerFactory.getLogger(classOf[LocalPubSub])

  private var localPubSub: Process = _

  var pubSubEnvironment: PubSubEnvironment = _

  private var started = false

  def pubSubEmulatorUrl = s"http://${pubSubEnvironment.host}:${pubSubEnvironment.port}"

  override protected def beforeAll(): Unit = {
    logger.info("Starting pubsub emulator")

    localPubSub = Process(s"gcloud beta emulators pubsub start").run(
      ProcessLogger(
        line => {
          logger.debug(line)

          line match {
            case StartedExpression(port) =>
              logger.info(s"Pubsub emulator started on port $port")

              started = true
              pubSubEnvironment = PubSubEnvironment("localhost", port.toInt)
            case _ =>
          }
        }
      )
    )

    implicit val patienceConfig =
      PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

    logger.info("Waiting for pubsub emulator to start...")

    eventually {
      started shouldBe true
    }

    super.beforeAll()
  }

  override def afterAll() {
    try super.afterAll()
    finally {
      val maybePid = Try(
        (Process("ps" :: "-ax" :: Nil) #| Process("grep" :: "-v" :: "grep" :: Nil) #| Process(
          "grep" :: s"cloud-pubsub-emulator.*port=${pubSubEnvironment.port}" :: Nil
        ) #| Process("awk" :: "{print $1}" :: Nil)).!!.trim()
      ) match {
        case Success(pid) if pid.length > 0 => Some(pid)
        case _                              => None
      }

      logger.info(s"Terminating pubsub emulator process with pid $maybePid")

      maybePid match {
        case Some(pid) =>
          val code = Process("kill" :: "-9" :: pid :: Nil).!
          logger.info(s"PubSub emulator killed pid:$pid code:$code")
        case None =>
          logger.warn(s"Process for PubSub emulator with port ${pubSubEnvironment.port} not found.")
          Try(localPubSub.destroy())
      }
    }
  }
}

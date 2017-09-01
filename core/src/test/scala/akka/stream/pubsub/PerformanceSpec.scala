package akka.stream.pubsub

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.PubsubMessage
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object PerformanceSpec {
  val MessageTemplate: (Int, String) => String = (id: Int, message: String) =>
    s"""{"id": $id, "message": "$message", "date": "${LocalDate.now()}""""
}

class PerformanceSpec
    extends TestKit(ActorSystem("PerformanceTest"))
    with WordSpecLike
    with Eventually
    with Matchers
    with ScalaFutures
    with PubSubTestKit
    with BeforeAndAfterAll {

  import PerformanceSpec._

  private val log = LoggerFactory.getLogger(PerformanceSpec.getClass)

  implicit val mat = ActorMaterializer()

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "the stream" should {
    "process lots of data" ignore {
      val settings = newTestSetup()

      val insert = Future {
        log.info("Insert messages...")

        var i = 1

        while (i <= 2000) {
          val messages = for (j <- 1 to 1000)
            yield
              PubsubMessage(ByteString.copyFromUtf8(MessageTemplate(i * j, s"Blah blah $i - $j")))

          publishMessages(settings, messages: _*)

          i += 1
        }

        log.info("Finished inserting messages...")
      }

      Await.ready(insert, 30.seconds)

      val (_, _, subscription) = settings

      import PubSubAcknowledgeFlow._

      val source  = Source.fromGraph(PubSubSource(pubSubUrl, subscription.fullName))
      val ackFlow = PubSubAcknowledgeFlow(subscription.fullName, pubSubUrl).batched(size = 500)

      val start = System.currentTimeMillis()

      val future = source.async
        .via(ackFlow)
        .async
        .take(1000000)
        .runWith(Sink.ignore)

      Await.ready(future, 45.seconds)

      val end = System.currentTimeMillis()

      log.info(s"Took ${end - start} millis.")
    }
  }
}

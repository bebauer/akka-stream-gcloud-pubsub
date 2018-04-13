package akka.stream.pubsub

import java.time.LocalDate

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.testkit.TestKit
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.testkit.{DockerPubSub, PubSubTestKit}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import org.slf4j.LoggerFactory

import scala.concurrent.Await
import scala.concurrent.duration._

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
    with DockerPubSub
    with BeforeAndAfterAll {

  import PerformanceSpec._

  private val log = LoggerFactory.getLogger(PerformanceSpec.getClass)

  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  override val publishTimeout: FiniteDuration = 20.seconds

  "the stream" should {
    "process lots of data" ignore {
      val settings = newTestSetup()

      log.info("Insert messages...")

      var i = 1

      while (i <= 2000) {
        val messages = for (j <- 1 to 1000) yield MessageTemplate(i * j, s"Blah blah $i - $j")

        publishMessages(settings, messages: _*) should have size messages.size

        log.info(s"$i - published ${messages.size} messages")

        i += 1
      }

      log.info("Finished inserting messages...")

      val (_, _, subscription) = settings

      val source =
        Source.fromGraph(PubSubSource(subscription, SubscriberStub.pubsubUrlToSettings(pubSubUrl)))
      val ackFlow =
        PubSubAcknowledgeFlow(subscription, SubscriberStub.pubsubUrlToSettings(pubSubUrl))

      val start = System.currentTimeMillis()

      val future = source.async
        .groupedWithin(500, 100.milliseconds)
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

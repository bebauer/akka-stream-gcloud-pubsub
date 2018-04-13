package akka.stream.pubsub

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.google.pubsub.v1.ReceivedMessage
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.testkit.{DockerPubSub, PubSubTestKit}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PubSubAcknowledgeFlowSpec
    extends TestKit(ActorSystem("AckTest"))
    with WordSpecLike
    with Eventually
    with Matchers
    with ScalaFutures
    with PubSubTestKit
    with BeforeAndAfterAll
    with DockerPubSub {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def afterAll() {
    super.afterAll()

    TestKit.shutdownActorSystem(system)
  }

  "the flow" should {
    "acknowledge messages" in {
      val settings = newTestSetup()
      val messages = for (i <- 1 to 200) yield s"message-$i"

      publishMessages(settings, messages: _*)

      val ackFlow =
        PubSubAcknowledgeFlow(settings._3.fullName, SubscriberStub.pubsubUrlToSettings(pubSubUrl))

      val (pub, sub) = TestSource
        .probe[Seq[ReceivedMessage]]
        .via(ackFlow)
        .toMat(TestSink.probe[Seq[ReceivedMessage]])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext(
        Seq(pullReceivedMessages(settings, 70): _*)
      )
      val acknowledged = sub.expectNext()

      acknowledged.size shouldBe 70
    }
  }
}

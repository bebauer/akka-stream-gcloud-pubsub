package akka.stream.pubsub

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{PubsubMessage, ReceivedMessage}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class PubSubAcknowledgeFlowSpec
    extends TestKit(ActorSystem("AckTest"))
    with WordSpecLike
    with Eventually
    with Matchers
    with ScalaFutures
    with PubSubTestKit
    with BeforeAndAfterAll {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "the flow" should {
    "acknowledge messages" in {
      val settings = newTestSetup()
      val messages = for (i <- 1 to 200)
        yield PubsubMessage(ByteString.copyFromUtf8(s"message-$i"))

      publishMessages(settings, messages: _*)

      val ackFlow = PubSubAcknowledgeFlow(settings._3.fullName, pubSubUrl)

      val (pub, sub) = TestSource
        .probe[Seq[ReceivedMessage]]
        .via(ackFlow)
        .toMat(TestSink.probe[Seq[ReceivedMessage]])(Keep.both)
        .run()

      sub.request(1)
      pub.sendNext(pullMessages(settings, 70))
      val acknowledged = sub.expectNext()

      acknowledged.size shouldBe 70
    }
  }

  "the batching flow" should {
    "acknowledge messages" in {
      import PubSubAcknowledgeFlow._

      val settings = newTestSetup()
      val messages = for (i <- 1 to 200)
        yield PubsubMessage(ByteString.copyFromUtf8(s"message-$i"))

      publishMessages(settings, messages: _*)

      val ackFlow = PubSubAcknowledgeFlow(settings._3.fullName, pubSubUrl).batched()

      val (pub, sub) = TestSource
        .probe[ReceivedMessage]
        .via(ackFlow)
        .toMat(TestSink.probe[ReceivedMessage])(Keep.both)
        .run()

      val pulledMessages = pullMessages(settings, 100).take(70)

      sub.request(70)
      pulledMessages.foreach(pub.sendNext)

      sub.expectNextN(70) shouldEqual pulledMessages
    }
  }

  "the parallel batching flow" should {
    "acknowledge messages" in {
      import PubSubAcknowledgeFlow._

      val settings = newTestSetup()
      val messages = for (i <- 1 to 200)
        yield PubsubMessage(ByteString.copyFromUtf8(s"message-$i"))

      publishMessages(settings, messages: _*)

      val ackFlow = PubSubAcknowledgeFlow(settings._3.fullName, pubSubUrl).parallel(3).batched(10)

      val (pub, sub) = TestSource
        .probe[ReceivedMessage]
        .via(ackFlow)
        .toMat(TestSink.probe[ReceivedMessage])(Keep.both)
        .run()

      val pulledMessages = pullMessages(settings, 100).take(70)

      sub.request(70)
      pulledMessages.foreach(pub.sendNext)

      sub.expectNextN(70) should contain theSameElementsAs pulledMessages
    }
  }
}

package akka.stream.pubsub

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.stream.pubsub.PubSubAcknowledgeFlow._
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.testkit.TestKit
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.PubsubMessage
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.tagobjects.Slow
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.Await
import scala.concurrent.duration._

class StreamSpec
    extends TestKit(ActorSystem("StreamTest"))
    with WordSpecLike
    with Eventually
    with Matchers
    with ScalaFutures
    with PubSubTestKit
    with BeforeAndAfterAll {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def afterAll() {
    super.afterAll()

    TestKit.shutdownActorSystem(system)
  }

  "the PubSub stream" should {
    "acknowledge all processed messages" taggedAs Slow in {
      val (project, topic, subscription) = newTestSetup()
      val messages = for (i <- 1 to 20)
        yield PubsubMessage(ByteString.copyFromUtf8(s"message-$i"))

      publishMessages((project, topic, subscription), messages: _*)

      val source  = Source.fromGraph(PubSubSource(pubSubUrl, subscription.fullName))
      val ackFlow = PubSubAcknowledgeFlow(subscription.fullName, pubSubUrl).parallel(2).batched(5)

      val (killSwitch, results) = source
        .viaMat(KillSwitches.single)(Keep.right)
        .viaMat(ackFlow)(Keep.left)
        .toMat(Sink.seq)(Keep.both)
        .run()

      Thread.sleep(15.seconds.toMillis)

      killSwitch.shutdown()

      Await.result(results, 1.second).size should equal(messages.size)
    }

    "re-pull unacknowledged messages" taggedAs Slow in {
      val (project, topic, subscription) = newTestSetup()
      val messages = for (i <- 1 to 20)
        yield PubsubMessage(ByteString.copyFromUtf8(s"message-$i"))

      publishMessages((project, topic, subscription), messages: _*)

      val source = Source.fromGraph(PubSubSource(pubSubUrl, subscription.fullName))

      val (killSwitch, results) = source
        .viaMat(KillSwitches.single)(Keep.right)
        .toMat(Sink.seq)(Keep.both)
        .run()

      Thread.sleep(15.seconds.toMillis)

      killSwitch.shutdown()

      Await.result(results, 1.second).size should be > messages.size
    }
  }
}

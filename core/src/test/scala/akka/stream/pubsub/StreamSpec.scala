package akka.stream.pubsub

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Keep, Sink, Source}
import akka.stream.{ActorMaterializer, KillSwitches}
import akka.testkit.TestKit
import com.google.api.gax.core.NoCredentialsProvider
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.testkit.{DockerPubSub, PubSubTestKit}
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
    with DockerPubSub
    with BeforeAndAfterAll {

  implicit val mat: ActorMaterializer = ActorMaterializer()

  override def afterAll() {
    super.afterAll()

    TestKit.shutdownActorSystem(system)
  }

  "the PubSub stream" should {
    "acknowledge all processed messages" taggedAs Slow in {
      val (project, topic, subscription) = newTestSetup()
      val messages                       = for (i <- 1 to 20) yield s"message-$i"

      publishMessages((project, topic, subscription), messages: _*)

      val source =
        Source.fromGraph(
          PubSubSource(subscription.fullName,
                       SubscriberStub
                         .Settings(pubSubUrl)
                         .copy(credentialsProvider = NoCredentialsProvider.create()))
        )
      val ackFlow =
        PubSubAcknowledgeFlow(subscription.fullName,
                              SubscriberStub
                                .Settings(pubSubUrl)
                                .copy(credentialsProvider = NoCredentialsProvider.create()))

      val (killSwitch, results) = source
        .viaMat(KillSwitches.single)(Keep.right)
        .groupedWithin(500, 100.milliseconds)
        .viaMat(ackFlow)(Keep.left)
        .mapConcat(collection.immutable.Seq(_: _*))
        .toMat(Sink.seq)(Keep.both)
        .run()

      Thread.sleep(15.seconds.toMillis)

      killSwitch.shutdown()

      Await.result(results, 1.second).size should equal(messages.size)
    }

    "re-pull unacknowledged messages" taggedAs Slow in {
      val (project, topic, subscription) = newTestSetup()
      val messages                       = for (i <- 1 to 20) yield s"message-$i"

      publishMessages((project, topic, subscription), messages: _*)

      val source =
        Source.fromGraph(
          PubSubSource(subscription.fullName,
                       SubscriberStub
                         .pubsubUrlToSettings(pubSubUrl)
                         .copy(credentialsProvider = NoCredentialsProvider.create()))
        )

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

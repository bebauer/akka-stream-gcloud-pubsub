package akka.stream.pubsub

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Keep
import akka.stream.testkit.scaladsl.{TestSink, TestSource}
import akka.testkit.TestKit
import com.google.api.gax.core.NoCredentialsProvider
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.testkit.{DockerPubSub, PubSubTestKit}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class PubSubPublishFlowSpec
    extends TestKit(ActorSystem("PublishTest"))
    with WordSpecLike
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

  "the flow" should {
    "publish all messages" in {
      val settings      = newTestSetup()
      val (_, topic, _) = settings
      val messages      = for (i <- 1 to 200) yield PubsubMessage(s"message-$i").build()

      val (pub, sub) = TestSource
        .probe[PublishMessages]
        .via(
          PubSubPublishFlow(topic,
                            PublisherStub
                              .pubsubUrlToSettings(pubSubUrl)
                              .copy(credentialsProvider = NoCredentialsProvider.create()))
        )
        .toMat(TestSink.probe[PublishedIds])(Keep.both)
        .run()

      sub.request(1)

      pub.sendNext(messages)

      val publishedIds = sub.expectNext()
      publishedIds should have size messages.size

      Thread.sleep(2.seconds.toMillis)

      val pulled = pullMessages(settings, messages.size).map(_.getMessageId)

      pulled shouldEqual publishedIds
    }
  }
}

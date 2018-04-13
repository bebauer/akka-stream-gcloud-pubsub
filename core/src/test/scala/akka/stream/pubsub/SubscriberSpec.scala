package akka.stream.pubsub

import akka.actor.ActorSystem
import akka.actor.Status.Failure
import akka.stream.pubsub.Subscriber.{FetchMessages, MessagesPulled}
import akka.testkit.TestKit
import com.google.api.gax.rpc.NotFoundException
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.testkit.{DockerPubSub, PubSubTestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

class SubscriberSpec
    extends TestKit(ActorSystem("subscriber-test"))
    with WordSpecLike
    with Matchers
    with PubSubTestKit
    with DockerPubSub
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  "The subscriber" should {
    "respond with pulled messages" in {
      val settings             = newTestSetup()
      val (_, _, subscription) = settings

      val subscriber = system.actorOf(
        Subscriber.props(testActor, SubscriberStub.pubsubUrlToSettings(pubSubUrl), subscription)
      )

      publishMessages(settings, "A", "B", "C")

      subscriber ! FetchMessages(3)

      expectMsgPF() {
        case MessagesPulled(messages) =>
          messages.size shouldBe 3
      }
    }

    "respond with exception on error" in {
      val settings     = newTestSetup()
      val subscription = ProjectSubscriptionName(settings._1, "doesnotexist")

      val subscriber = system.actorOf(
        Subscriber.props(testActor, SubscriberStub.pubsubUrlToSettings(pubSubUrl), subscription)
      )

      publishMessages(settings, "A", "B", "C")

      subscriber ! FetchMessages(3)

      expectMsgPF() {
        case Failure(_: NotFoundException) =>
      }
    }
  }
}

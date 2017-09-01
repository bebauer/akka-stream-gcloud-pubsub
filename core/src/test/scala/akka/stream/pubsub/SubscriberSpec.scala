package akka.stream.pubsub

import java.util.UUID

import akka.actor.ActorSystem
import akka.actor.Status.Failure
import akka.stream.pubsub.Subscriber.{FetchMessages, MessagesPulled}
import akka.testkit.TestKit
import com.google.pubsub.v1.pubsub.Subscription
import de.codecentric.akka.stream.gcloud.pubsub.client.{ProjectName, SubscriptionName, TopicName}
import io.grpc.StatusRuntimeException
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.ExecutionContextExecutor

class SubscriberSpec
    extends TestKit(ActorSystem("subscriber-test"))
    with WordSpecLike
    with Matchers
    with PubSubClientHelpers
    with BeforeAndAfterAll {

  implicit val executionContext: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  override def afterAll(): Unit = {
    system.terminate()
    super.afterAll()
  }

  "The subscriber" should {
    "respond with pulled messages" in {
      val uuid = UUID.randomUUID().toString

      val project          = ProjectName(s"test-$uuid")
      val topic            = TopicName(project, "test")
      val subscriptionName = SubscriptionName(project, "testSubscription")
      val subscription     = Subscription(subscriptionName.fullName, topic.fullName)

      val subscriber = system.actorOf(
        Subscriber.props(testActor, "http://localhost:8085", subscriptionName.fullName)
      )

      withClient { client =>
        createTopic(client, topic)

        client.createSubscription(subscription).map {
          _.name shouldBe subscriptionName.fullName
        }

        publishMessage(client, topic.fullName, "A")
        publishMessage(client, topic.fullName, "B")
        publishMessage(client, topic.fullName, "C")

        subscriber ! FetchMessages(3)

        expectMsgPF() {
          case MessagesPulled(messages) =>
            messages.size shouldBe 3
        }
      }
    }

    "respond with exception on error" in {
      val uuid = UUID.randomUUID().toString

      val project          = ProjectName(s"test-$uuid")
      val topic            = TopicName(project, "test")
      val subscriptionName = SubscriptionName(project, "testSubscription")

      val subscriber = system.actorOf(
        Subscriber.props(testActor, "http://localhost:8085", subscriptionName.fullName)
      )

      withClient { client =>
        createTopic(client, topic)

        publishMessage(client, topic.fullName, "A")
        publishMessage(client, topic.fullName, "B")
        publishMessage(client, topic.fullName, "C")

        subscriber ! FetchMessages(3)

        expectMsgPF() {
          case Failure(ex: StatusRuntimeException) =>
        }
      }
    }
  }
}

package de.codecentric.akka.stream.gcloud.pubsub.client

import java.util.UUID

import akka.stream.pubsub.PubSubClientHelpers
import com.google.pubsub.v1.pubsub._
import io.grpc.StatusRuntimeException
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{AsyncWordSpec, Matchers}

import scala.concurrent.Future

class PubSubSubscriberSpec
    extends AsyncWordSpec
    with Matchers
    with ScalaFutures
    with PubSubClientHelpers {

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  "The PubSubSubscriber" should {

    "create a subscription" in {
      withClientAsync { client =>
        val uuid = UUID.randomUUID().toString

        val project          = ProjectName(s"test-$uuid")
        val topic            = TopicName(project, "test")
        val subscriptionName = SubscriptionName(project, "testSubscription")
        val subscription     = Subscription(subscriptionName.fullName, topic.fullName)

        createTopic(client, topic)

        client.createSubscription(subscription).map {
          _.name shouldBe subscriptionName.fullName
        }
      }
    }

    "pull messages" in {
      withClientAsync { client =>
        val uuid = UUID.randomUUID().toString

        val project          = ProjectName(s"test-$uuid")
        val topic            = TopicName(project, "test")
        val subscriptionName = SubscriptionName(project, "testSubscription")
        val subscription     = Subscription(subscriptionName.fullName, topic.fullName)

        createTopic(client, topic)

        client
          .createSubscription(subscription)
          .flatMap { _ =>
            publishMessage(client, topic.fullName, "A")
            publishMessage(client, topic.fullName, "B")
            publishMessage(client, topic.fullName, "C")

            client.pull(
              PullRequest(subscriptionName.fullName, returnImmediately = true, maxMessages = 10)
            )
          }
          .map {
            _.size shouldBe 3
          }
      }
    }

    "pull fails with invalid subscription" in {
      withClientAsync { client =>
        val uuid = UUID.randomUUID().toString

        val project          = ProjectName(s"test-$uuid")
        val subscriptionName = SubscriptionName(project, "testSubscription")

        recoverToSucceededIf[StatusRuntimeException] {
          client
            .pull(
              PullRequest(subscriptionName.fullName, returnImmediately = true, maxMessages = 10)
            )
        }
      }
    }

    "acknowledge messages" in {
      withClientAsync { client =>
        val uuid = UUID.randomUUID().toString

        val project          = ProjectName(s"test-$uuid")
        val topic            = TopicName(project, "test")
        val subscriptionName = SubscriptionName(project, "testSubscription")
        val subscription     = Subscription(subscriptionName.fullName, topic.fullName)

        createTopic(client, topic)

        client
          .createSubscription(subscription)
          .flatMap { _ =>
            publishMessage(client, topic.fullName, "A")

            client.pull(
              PullRequest(subscriptionName.fullName, returnImmediately = true, maxMessages = 10)
            )
          }
          .flatMap { response =>
            response.size shouldBe 1

            Future.sequence(response.map { message =>
              client.acknowledge(
                AcknowledgeRequest(subscriptionName.fullName, Seq(message.ackId))
              )
            })
          }
          .map { _ =>
            succeed
          }
      }
    }
  }
}

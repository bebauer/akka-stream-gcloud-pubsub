package akka.stream.pubsub

import java.util.UUID

import com.google.pubsub.v1.pubsub._
import de.codecentric.akka.stream.gcloud.pubsub.client.{
  ProjectName,
  PubSubClient,
  SubscriptionName,
  TopicName
}

import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.concurrent.duration._

trait PubSubTestKit {
  type PubSubTestSettings = (ProjectName, TopicName, SubscriptionName)

  implicit val executionContext: ExecutionContextExecutor =
    scala.concurrent.ExecutionContext.global

  val pubSubUrl: String = "http://localhost:8085"

  lazy val client: PubSubClient = PubSubClient(pubSubUrl)

  def newTestSetup(): PubSubTestSettings = {
    val project      = ProjectName(s"test-${UUID.randomUUID().toString}")
    val topic        = TopicName(project, "top")
    val subscription = SubscriptionName(project, "subs")

    Await.ready(client.createTopic(Topic(topic.fullName)), 10.seconds)
    Await.ready(client.createSubscription(Subscription(subscription.fullName, topic.fullName)),
                10.seconds)

    (project, topic, subscription)
  }

  def publishMessages(settings: PubSubTestSettings, messages: PubsubMessage*): Unit = {
    val (_, topic, _) = settings

    Await.ready(client.publish(topic.fullName, messages), 10.seconds)
  }

  def pullMessages(settings: PubSubTestSettings, amount: Int): Seq[ReceivedMessage] = {
    val (_, _, subscription) = settings

    Await.result(client.pull(
                   PullRequest(subscription = subscription.fullName,
                               returnImmediately = true,
                               maxMessages = amount)
                 ),
                 10.seconds)
  }
}

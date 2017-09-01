package akka.stream.pubsub

import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{PubsubMessage, Topic}
import de.codecentric.akka.stream.gcloud.pubsub.client.{PubSubClient, PubSubPublisher, TopicName}
import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait PubSubClientHelpers extends ScalaFutures {
  def withClient(block: PubSubClient => Unit): Unit = {
    def client = PubSubClient("http://localhost:8085")(scala.concurrent.ExecutionContext.global)

    try {
      block(client)
    } finally {
      client.shutDown()
    }
  }

  def withClientAsync(block: PubSubClient => Future[Assertion]): Future[Assertion] = {
    def client = PubSubClient("http://localhost:8085")(scala.concurrent.ExecutionContext.global)

    try {
      block(client)
    } finally {
      client.shutDown()
    }
  }

  def createTopic(publisher: PubSubPublisher, topicName: TopicName): Unit =
    whenReady(publisher.createTopic(Topic(topicName.fullName))) { topic =>
      println(s"topic created: $topic")
    }

  def publishMessage(publisher: PubSubPublisher, topic: String, message: String): Seq[String] =
    Await.result(
      publisher.publish(topic, List(PubsubMessage(ByteString.copyFrom(message, "UTF-8")))),
      10.seconds
    )
}

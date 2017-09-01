package de.codecentric.akka.stream.gcloud.pubsub.benchmark

import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{GetSubscriptionRequest, PubsubMessage, Subscription, Topic}
import de.codecentric.akka.stream.gcloud.pubsub.client.PubSubClient

import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.duration._

object TestData extends App with Config {

  def preparePubSub()(implicit executionContextExecutor: ExecutionContextExecutor): Unit = {
    val client = PubSubClient()

    try {
      val maybeTopic = Await.result(client.getTopic(topic), 10.seconds)
      if (maybeTopic.isEmpty) {
        Await.ready(client.createTopic(Topic(name = topic)), 10.seconds)
      }

      val maybeSubscription = Await.result(
        client.getSubscription(GetSubscriptionRequest(subscription = subscription)),
        10.seconds
      )
      if (maybeSubscription.isEmpty) {
        Await.ready(client.createSubscription(
                      subscription =
                        Subscription(name = subscription, topic = topic, ackDeadlineSeconds = 10)
                    ),
                    10.seconds)
      }
    } finally {
      client.shutDown()
    }
  }

  def insertTestData(
      amount: Int
  )(implicit executionContextExecutor: ExecutionContextExecutor): Unit = {
    println(s"inserting $amount test data...")

    val client = PubSubClient()

    var requests: List[Future[Seq[String]]] = Nil
    var first: Int                          = 1
    var last: Int                           = 1

    for (i <- (1 to amount).grouped(100)) {
      if (requests.size == 50) {
        Await.ready(Future.sequence(requests), 30.seconds)

        println(s"published messages $first to $last")

        requests = Nil
        first = i.head
      }

      requests = requests :+ client.publish(
        topicName = topic,
        i.map(index => PubsubMessage(ByteString.copyFromUtf8(s"A" * 1024 * 8))) // 8 Kb Message
      )

      last = i.last
    }

    if (requests.nonEmpty) {
      Await.ready(Future.sequence(requests), 30.seconds)

      println(s"published messages $first to $last")
    }

    println("test data inserted")
  }

  preparePubSub()(ExecutionContext.global)
  insertTestData(1000000)(ExecutionContext.global)
}

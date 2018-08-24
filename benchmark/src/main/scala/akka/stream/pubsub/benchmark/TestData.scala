package akka.stream.pubsub.benchmark

import com.google.pubsub.v1.PublishResponse
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.syntax._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}

object TestData extends App with Config {

  def preparePubSub()(implicit executionContextExecutor: ExecutionContextExecutor): Unit = {
    val subscriptionAdmin = SubscriptionAdminClient(SubscriptionAdminClient.Settings())

    try {
      val topicAdmin = TopicAdminClient(TopicAdminClient.Settings())

      try {
        val maybeTopic = Await.result(topicAdmin.getTopicOptionAsync(topic), 10.seconds)
        if (maybeTopic.isEmpty) {
          topicAdmin.createTopic(topic)
        }

        val maybeSubscription = Await.result(
          subscriptionAdmin.getSubscriptionOptionAsync(subscription),
          10.seconds
        )
        if (maybeSubscription.isEmpty) {
          subscriptionAdmin.createSubscription(
            Subscription(subscriptionName = subscription, topicName = topic)
          )
        }
      } finally {
        topicAdmin.close()
      }

    } finally {
      subscriptionAdmin.close()
    }
  }

  def insertTestData(
      amount: Int
  )(implicit executionContextExecutor: ExecutionContextExecutor): Unit = {
    println(s"inserting $amount test data...")

    val publisher = PublisherStub(PublisherStub.Settings())

    try {
      var requests: List[Future[PublishResponse]] = Nil
      var first: Int                              = 1
      var last: Int                               = 1

      for (i <- (1 to amount).grouped(100)) {
        if (requests.lengthCompare(50) == 0) {
          Await.ready(Future.sequence(requests), 30.seconds)

          println(s"published messages $first to $last")

          requests = Nil
          first = i.head
        }

        requests = requests :+ publisher.publishAsync(
          topic = topic,
          messages = i.map(_ => PubsubMessage(s"A" * 1024 * 8).build()) // 8 Kb Message
        )

        last = i.last
      }

      if (requests.nonEmpty) {
        Await.ready(Future.sequence(requests), 30.seconds)

        println(s"published messages $first to $last")
      }

      println("test data inserted")
    } finally {
      publisher.close()
    }
  }

  preparePubSub()(ExecutionContext.global)
  insertTestData(1000000)(ExecutionContext.global)
}

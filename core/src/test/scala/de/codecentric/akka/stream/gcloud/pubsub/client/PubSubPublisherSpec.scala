package de.codecentric.akka.stream.gcloud.pubsub.client

import java.nio.charset.Charset
import java.util.UUID

import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{ListTopicsRequest, PubsubMessage, Topic}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpecLike}

import scala.concurrent.ExecutionContextExecutor

class PubSubPublisherSpec extends WordSpecLike with Matchers with ScalaFutures {

  implicit val executionContext: ExecutionContextExecutor =
    scala.concurrent.ExecutionContext.global

  implicit override val patienceConfig =
    PatienceConfig(timeout = Span(60, Seconds), interval = Span(500, Millis))

  "The PubSubPublisher" should {

    "list all topics" in {
      withClient { publisher =>
        val uuid = UUID.randomUUID().toString

        val project = ProjectName(s"test-$uuid")
        val topic   = TopicName(project, "test")

        createTopic(publisher, topic)

        whenReady(publisher.listTopics(ListTopicsRequest(project.fullName))) { topics =>
          topics.map(_.name) shouldBe List(topic.fullName)
        }
      }
    }

    "publish messages" in {
      withClient { publisher =>
        val uuid = UUID.randomUUID().toString

        val project = ProjectName(s"test-$uuid")
        val topic   = TopicName(project, "test")

        createTopic(publisher, topic)

        whenReady(
          publisher
            .publish(
              topic.fullName,
              List(PubsubMessage(ByteString.copyFrom("TEXT1", Charset.forName("UTF-8"))),
                   PubsubMessage(ByteString.copyFrom("TEXT2", Charset.forName("UTF-8"))))
            )
        ) { ids =>
          ids.size shouldBe 2
        }
      }
    }

    "get existing topic" in {
      withClient { publisher =>
        val uuid = UUID.randomUUID().toString

        val project = ProjectName(s"test-$uuid")
        val topic   = TopicName(project, "test")

        createTopic(publisher, topic)

        whenReady(publisher.getTopic(topic.fullName)) { receivedTopic =>
          receivedTopic.map(_.name) shouldBe Some(topic.fullName)
        }
      }
    }

    "get not existing topic" in {
      withClient { publisher =>
        val uuid = UUID.randomUUID().toString

        val project = ProjectName(s"test-$uuid")
        val topic   = TopicName(project, "test")

        whenReady(publisher.getTopic(topic.fullName)) { receivedTopic =>
          receivedTopic shouldBe None
        }
      }
    }

    def withClient(block: PubSubPublisher => Unit) = {
      def publisher = PubSubClient("http://localhost:8085")

      try {
        block(publisher)
      } finally {
        publisher.shutDown()
      }
    }

    def createTopic(publisher: PubSubPublisher, topicName: TopicName) =
      whenReady(publisher.createTopic(Topic(topicName.fullName))) { topic =>
        println(s"topic created: $topic")
      }
  }
}

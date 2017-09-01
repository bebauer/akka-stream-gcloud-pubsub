package de.codecentric.akka.stream.gcloud.pubsub.client

import com.google.auth.Credentials
import com.google.pubsub.v1.pubsub._
import io.grpc.Channel
import io.grpc.auth.MoreCallCredentials

import scala.concurrent.{ExecutionContext, Future}

trait PubSubPublisher {

  implicit val executionContext: ExecutionContext

  def getChannel: Channel
  def getCredentials: Credentials

  private def publisherStub = PublisherGrpc
    .stub(getChannel)
    .withCallCredentials(MoreCallCredentials.from(getCredentials))

  def listTopics(request: ListTopicsRequest): Future[Seq[Topic]] =
    publisherStub
      .listTopics(request)
      .map(_.topics)

  def createTopic(topic: Topic): Future[Topic] =
    publisherStub
      .createTopic(topic)

  def getTopic(topicName: String): Future[Option[Topic]] =
    listTopics(ListTopicsRequest(TopicName(topicName).projectName.fullName))
      .map(topics => topics.find(_.name == topicName))

  def publish(topicName: String, messages: Seq[PubsubMessage]): Future[Seq[String]] =
    publisherStub.publish(PublishRequest(topicName, messages)).map(_.messageIds)
}

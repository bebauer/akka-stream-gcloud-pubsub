package akka.stream.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings
import com.google.pubsub.v1.ProjectTopicName
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.syntax._
import scala.collection.JavaConverters._

private[pubsub] object Publisher {
  def name(context: String, id: Int): String = s"pubsub-publisher-$context-$id"

  def props(source: ActorRef, settings: PublisherStubSettings, topic: ProjectTopicName): Props =
    Props(new Publisher(source, settings, topic))

  final case class Publish(messages: PublishMessages)

  final case class Published(ids: PublishedIds)
}

private[pubsub] class Publisher(source: ActorRef,
                                settings: PublisherStubSettings,
                                topic: ProjectTopicName)
    extends Actor
    with ActorLogging {
  import Publisher._

  val stub = PublisherStub(settings)

  import context.dispatcher

  override def postStop(): Unit = stub.close()

  override def receive: Receive = {
    case Publish(messages) =>
      stub
        .publishAsync(topic = topic, messages = messages)
        .map(response => Published(response.getMessageIdsList.asScala))
        .pipeTo(source)
  }
}

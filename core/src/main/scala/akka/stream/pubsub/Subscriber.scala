package akka.stream.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings
import com.google.pubsub.v1.ProjectSubscriptionName
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.syntax._

private[pubsub] object Subscriber {
  def name(context: String, id: Int): String = s"pubsub-subscriber-$context-$id"

  def props(source: ActorRef,
            settings: SubscriberStubSettings,
            subscription: ProjectSubscriptionName): Props =
    Props(new Subscriber(source, settings, subscription))

  final case class MessagesPulled(messages: ReceivedMessages)

  final case class FetchMessages(count: Int)
}

private[pubsub] class Subscriber(source: ActorRef,
                                 settings: SubscriberStubSettings,
                                 subscription: ProjectSubscriptionName)
    extends Actor
    with ActorLogging {
  import Subscriber._

  val stub = SubscriberStub(settings)

  import context.dispatcher

  override def postStop(): Unit = stub.close()

  override def receive: Receive = {
    case FetchMessages(n) =>
      stub
        .pullAsync(subscription = subscription, returnImmediately = true, maxMessages = n)
        .map(response => MessagesPulled(response.receivedMessages))
        .pipeTo(source)
  }
}

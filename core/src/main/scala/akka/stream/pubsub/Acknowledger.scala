package akka.stream.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings
import com.google.pubsub.v1.ProjectSubscriptionName
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.syntax._

private[pubsub] object Acknowledger {
  def name(context: String, id: Int): String = s"pubsub-acknowledger-$context-$id"

  def props(source: ActorRef,
            settings: SubscriberStubSettings,
            subscription: ProjectSubscriptionName): Props =
    Props(new Acknowledger(source, settings, subscription))

  final case class Acknowledge(messages: ReceivedMessages)

  final case class Acknowledged(messages: ReceivedMessages)
}

private[pubsub] class Acknowledger(source: ActorRef,
                                   settings: SubscriberStubSettings,
                                   subscription: ProjectSubscriptionName)
    extends Actor
    with ActorLogging {
  import Acknowledger._

  val stub = SubscriberStub(settings)

  import context.dispatcher

  override def postStop(): Unit = stub.close()

  override def receive: Receive = {
    case Acknowledge(messages) =>
      stub
        .acknowledgeAsync(subscription = subscription, ackIds = messages.map(_.getAckId))
        .map(_ => Acknowledged(messages))
        .pipeTo(source)
  }
}

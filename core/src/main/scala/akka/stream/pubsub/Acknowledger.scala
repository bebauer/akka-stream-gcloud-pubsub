package akka.stream.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import com.google.pubsub.v1.pubsub.AcknowledgeRequest

object Acknowledger {
  def name(context: String, id: Int): String = s"pubsub-acknowledger-$context-$id"

  def props(source: ActorRef, url: String, subscription: String): Props =
    Props(new Acknowledger(source, url, subscription))

  final case class Acknowledge(messages: ReceivedMessages)

  final case class Acknowledged(messages: ReceivedMessages)
}

class Acknowledger(source: ActorRef, val url: String, subscription: String)
    extends Actor
    with ActorLogging
    with Client {
  import Acknowledger._

  override def receive: Receive = {
    case Acknowledge(messages) =>
      client
        .acknowledge(AcknowledgeRequest(subscription, messages.map(_.ackId)))
        .map(_ => Acknowledged(messages))
        .pipeTo(source)
  }
}

package akka.stream.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.pattern.pipe
import com.google.pubsub.v1.pubsub.{PullRequest, ReceivedMessage}

object Subscriber {
  def name(context: String, id: Int): String = s"pubsub-subscriber-$context-$id"

  def props(source: ActorRef, url: String, subscription: String): Props =
    Props(new Subscriber(source, url, subscription))

  final case class MessagesPulled(messages: Seq[ReceivedMessage])

  final case class FetchMessages(count: Int)
}

class Subscriber(source: ActorRef, val url: String, subscription: String)
    extends Actor
    with ActorLogging
    with Client {
  import Subscriber._

  override def receive: Receive = {
    case FetchMessages(n) =>
      client
        .pull(PullRequest(subscription, maxMessages = n))
        .map(MessagesPulled)
        .pipeTo(source)
  }
}

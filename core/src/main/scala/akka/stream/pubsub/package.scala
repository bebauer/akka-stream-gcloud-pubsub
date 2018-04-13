package akka.stream

import com.google.pubsub.v1.{PubsubMessage, ReceivedMessage}

package object pubsub {
  type ReceivedMessages = Seq[ReceivedMessage]

  type PublishMessages = Seq[PubsubMessage]

  type PublishedIds = Seq[String]
}

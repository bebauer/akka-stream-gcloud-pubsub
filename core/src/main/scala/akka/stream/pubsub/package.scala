package akka.stream

import com.google.pubsub.v1.pubsub.ReceivedMessage

package object pubsub {
  type Seq[+A] = scala.collection.immutable.Seq[A]

  val Seq = scala.collection.immutable.Seq

  type ReceivedMessages = Seq[ReceivedMessage]
}

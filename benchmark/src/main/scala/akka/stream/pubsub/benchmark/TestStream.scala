package akka.stream.pubsub.benchmark

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.pubsub.{PubSubAcknowledgeFlow, PubSubSource}
import akka.stream.scaladsl._
import com.google.pubsub.v1.ReceivedMessage
import gcloud.scala.pubsub._

import scala.concurrent.{ExecutionContext, Future}

object TestStream extends Config with nl.grons.metrics.scala.DefaultInstrumented {

  def run(items: Int, ack: Boolean = true)(implicit actorSystem: ActorSystem,
                                           materializer: ActorMaterializer,
                                           executionContext: ExecutionContext): Future[String] = {

    val allSources = if (sourceCount > 1) {
      val sources = (1 to sourceCount).map(_ => Source.fromGraph(PubSubSource(subscription)))
      Source.combine(sources.head, sources.tail.head, sources.tail.tail: _*)(Merge(_))
    } else
      Source.fromGraph(PubSubSource(subscription))

    val ackFlow = Flow[ReceivedMessage]
      .groupedWeightedWithin(maxMessageSize, groupDuration)(_.getMessage.getData.size())
      .via(PubSubAcknowledgeFlow(subscription))
      .mapConcat(scala.collection.immutable.Seq(_: _*))

    val start = System.nanoTime()

    val pullingMeter     = metrics.meter("pulledMessages")
    val acknowledgeMeter = metrics.meter("ackedMessages")

    var stream = allSources.map { v =>
      pullingMeter.mark()
      v
    }.async

    if (ack) {
      stream = stream
        .via(ackFlow)
        .map { v =>
          acknowledgeMeter.mark()
          v
        }
        .async
    }

    stream = stream.take(items)

    val future = stream.runWith(Sink.ignore)

    future.map { _ =>
      val end = System.nanoTime()

      val duration = end - start

      s"Processing $items messages took $duration ns."
    }
  }
}

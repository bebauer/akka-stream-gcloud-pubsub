package akka.stream.pubsub

import akka.actor.Actor
import de.codecentric.akka.stream.gcloud.pubsub.client.PubSubClient

import scala.concurrent.ExecutionContextExecutor

trait Client { this: Actor =>

  val url: String

  implicit val executor: ExecutionContextExecutor = context.dispatcher

  lazy val client = PubSubClient(url)

  override def postStop(): Unit = client.shutDown()
}

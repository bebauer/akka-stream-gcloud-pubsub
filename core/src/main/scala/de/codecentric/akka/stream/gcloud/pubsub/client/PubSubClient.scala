package de.codecentric.akka.stream.gcloud.pubsub.client

import java.net.URL

import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import io.grpc.Channel
import io.grpc.netty.{NegotiationType, NettyChannelBuilder}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object PubSubClient {
  private val DefaultServiceScopes = List("https://www.googleapis.com/auth/cloud-platform",
                                          "https://www.googleapis.com/auth/pubsub")

  val DefaultPubSubUrl: String = "https://pubsub.googleapis.com:443"

  def apply()(implicit executor: ExecutionContextExecutor): PubSubClient =
    apply(DefaultPubSubUrl)

  def apply(url: String)(implicit executor: ExecutionContextExecutor): PubSubClient =
    apply(new URL(url))

  def apply(url: URL)(implicit executor: ExecutionContextExecutor): PubSubClient = {
    val tlsEnabled = url.getProtocol match {
      case "http"  => false
      case "https" => true
    }

    new PubSubClient(url.getHost, url.getPort, tlsEnabled)
  }

  def apply(host: String, port: Int, tlsEnabled: Boolean = true)(
      implicit executor: ExecutionContextExecutor
  ): PubSubClient = new PubSubClient(host, port, tlsEnabled)
}

class PubSubClient(host: String, port: Int, tlsEnabled: Boolean = true)(
    implicit executor: ExecutionContextExecutor
) extends PubSubSubscriber
    with PubSubPublisher with Config {
  import PubSubClient._

  import scala.collection.JavaConverters._

  private val channel = NettyChannelBuilder
    .forAddress(host, port)
    .maxInboundMessageSize(maxInboundMessageSize)
    .flowControlWindow(5000000)
    .negotiationType(if (tlsEnabled) NegotiationType.TLS else NegotiationType.PLAINTEXT)
    .executor(executor)
    .build()

  private val credentials =
    GoogleCredentials.getApplicationDefault.createScoped(DefaultServiceScopes.asJava)

  override def getChannel: Channel = channel

  override def getCredentials: Credentials = credentials

  override implicit val executionContext: ExecutionContext = executor

  def shutDown(): Unit =
    channel.shutdown()

}

package de.codecentric.akka.stream.gcloud.pubsub.client

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, FiniteDuration}

trait Config {
  private val config = ConfigFactory.load()

  protected val fetchSize: Int = config.getInt("akka.stream.gcloud.pubsub.source.fetchSize")
  protected val maxInboundMessageSize: Int =
    config.getInt("akka.stream.gcloud.pubsub.source.maxInboundMessageSize")
  protected val maxParallelFetchRequests: Int =
    config.getInt("akka.stream.gcloud.pubsub.source.maxParallelFetchRequests")
  protected val waitingTime: FiniteDuration = Duration(
    config.getString("akka.stream.gcloud.pubsub.source.waitingTime")
  ).asInstanceOf[FiniteDuration]
}

package akka.stream.pubsub

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, FiniteDuration}

private[pubsub] trait Config {
  private val config = ConfigFactory.load()

  protected val fetchSize: Int = config.getInt("akka.stream.gcloud.pubsub.source.fetchSize")
  protected val maxParallelFetchRequests: Int =
    config.getInt("akka.stream.gcloud.pubsub.source.maxParallelFetchRequests")
}

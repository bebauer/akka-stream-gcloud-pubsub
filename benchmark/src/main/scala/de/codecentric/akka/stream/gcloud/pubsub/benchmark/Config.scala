package de.codecentric.akka.stream.gcloud.pubsub.benchmark

import com.typesafe.config.ConfigFactory

trait Config {
  private val config = ConfigFactory.load()

  protected val subscription: String = config.getString("pubsub.subscription")
  protected val topic: String = config.getString("pubsub.topic")

  protected val httpHost: String = config.getString("http.host")
  protected val httpPort: Int = config.getInt("http.port")

  protected val sourceCount: Int = config.getInt("benchmark.source.count")
}

package akka.stream.pubsub.benchmark

import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.{Duration, FiniteDuration}

trait Config {
  private val config = ConfigFactory.load()

  protected val subscription: String = config.getString("pubsub.subscription")
  protected val topic: String        = config.getString("pubsub.topic")

  protected val httpHost: String = config.getString("http.host")
  protected val httpPort: Int    = config.getInt("http.port")

  protected val sourceCount: Int = config.getInt("benchmark.source.count")

  protected val maxMessageSize: Long = config.getLong("benchmark.max-message-size")

  protected val groupDuration: FiniteDuration = Duration(config.getString("benchmark.group-within"))
    .asInstanceOf[FiniteDuration]
}

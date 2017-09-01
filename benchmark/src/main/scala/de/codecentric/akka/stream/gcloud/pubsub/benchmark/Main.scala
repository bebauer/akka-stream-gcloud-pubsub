package de.codecentric.akka.stream.gcloud.pubsub.benchmark

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.codahale.metrics.Meter
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.io.StdIn

object Main
    extends App
    with Config
    with FailFastCirceSupport
    with nl.grons.metrics.scala.DefaultInstrumented {

  final case class TestConfig(amount: Int, pull: Int, ack: Boolean = true)

  implicit val system           = ActorSystem("benchmark-system")
  implicit val materializer     = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val routes =
  path("run") {
    post {
      withRequestTimeout(5.minutes) {
        entity(as[TestConfig]) { testConfig =>
          onSuccess(TestStream.run(testConfig.pull, testConfig.ack)) { result =>
            complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, result))
          }
        }
      }
    }
  } ~ path("data") {
    post {
      withRequestTimeout(5.minutes) {
        entity(as[TestConfig]) { testConfig =>
          TestData.preparePubSub()
          TestData.insertTestData(testConfig.amount)
          complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "Test data inserted."))
        }
      }
    }
  } ~ path("status") {
    get {
      complete(HttpEntity(ContentTypes.`text/plain(UTF-8)`, "All up and running :)"))
    }
  } ~ path("metrics") {
    get {
      val meterStrings = metricRegistry.getMeters.asScala.map {
        case (s, meter: Meter) =>
          s"""
           |$s
           |count = ${meter.getCount} events
           |mean rate = ${meter.getMeanRate} events/second
           |1-min rate = ${meter.getOneMinuteRate} events/second
           |5-min rate = ${meter.getFiveMinuteRate} events/second
           |15-min rate = ${meter.getFifteenMinuteRate} events/second
           |
           """.stripMargin
      } ++
      metricRegistry.getGauges.asScala.map {
        case (s, gauge) =>
          s"$s: ${gauge.getValue}"
      }

      complete(
        HttpEntity(ContentTypes.`text/plain(UTF-8)`, meterStrings.mkString("\n"))
      )
    }
  }

  val bindingFuture = Http().bindAndHandle(routes, httpHost, httpPort)

  println(s"Server online at http://$httpHost:$httpPort/\nPress RETURN to stop...")
  StdIn.readLine()
  bindingFuture
    .flatMap(_.unbind())
    .onComplete(_ => system.terminate())
}

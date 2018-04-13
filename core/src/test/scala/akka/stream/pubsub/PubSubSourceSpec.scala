package akka.stream.pubsub

import akka.actor.{ActorRef, ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import akka.stream.pubsub.Subscriber.{FetchMessages, MessagesPulled}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.GraphStageLogic
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.{TestKit, TestProbe}
import com.google.pubsub.v1.ReceivedMessage
import gcloud.scala.pubsub._
import gcloud.scala.pubsub.testkit.{DockerPubSub, PubSubTestKit}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

class PubSubSourceSpec
    extends TestKit(ActorSystem("source-test"))
    with WordSpecLike
    with Eventually
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll
    with PubSubTestKit
    with DockerPubSub {

  override def afterAll(): Unit =
    try {
      super.afterAll()
    } finally {
      system.terminate()
    }

  "the source " should {
    "tell the client actor to pull a message " in {
      implicit val executionContext: ExecutionContextExecutor =
        scala.concurrent.ExecutionContext.global

      val subscription = preparePubSub()

      implicit val system: ActorSystem = ActorSystem()

      try {
        implicit val mat: ActorMaterializer = ActorMaterializer()

        val source =
          Source.fromGraph(
            PubSubSource(subscription, SubscriberStub.pubsubUrlToSettings(pubSubEmulatorUrl))
          )

        val seq = source.take(200).runWith(Sink.seq)

        val messages = Await.result(seq, 10.seconds)

        messages.size shouldBe 200
      } finally {
        system.terminate()
      }
    }

    "terminate with failure on subscriber termination" in {
      val subscriberProbe    = TestProbe()
      var stageRef: ActorRef = null

      val source = createSource(subscriberProbe.ref, stageRef = _)

      implicit val mat: ActorMaterializer = ActorMaterializer()

      val sub = source.runWith(TestSink.probe[ReceivedMessage])

      sub.request(1)
      subscriberProbe.expectMsg(FetchMessages(1000))
      subscriberProbe.send(
        stageRef,
        Terminated(subscriberProbe.ref)(existenceConfirmed = true, addressTerminated = false)
      )
      sub.expectError().getMessage shouldBe "Subscriber actor terminated"
    }

    "terminate normally" in {
      val subscriberProbe    = TestProbe()
      var stageRef: ActorRef = null

      val source = createSource(subscriberProbe.ref, stageRef = _)

      implicit val mat: ActorMaterializer = ActorMaterializer()

      val sub = source.take(1).runWith(TestSink.probe[ReceivedMessage])

      sub.request(1)

      subscriberProbe.expectMsg(FetchMessages(1000))
      subscriberProbe.send(stageRef, MessagesPulled(createMessages(10)))
      sub.expectNextPF {
        case msg: ReceivedMessage => msg.getAckId shouldBe "id-1"
      }

      sub.expectComplete()
    }
  }

  private def createSource(subscriber: ActorRef, mappingCallback: (ActorRef => Unit)) =
    Source.fromGraph(
      new PubSubSource("projects/proj/subscriptions/subs",
                       SubscriberStub.pubsubUrlToSettings(pubSubEmulatorUrl)) {
        override protected def createSubscriber(system: ActorSystem,
                                                stageActor: GraphStageLogic.StageActor,
                                                id: String): ActorRef = {
          mappingCallback(stageActor.ref)
          subscriber
        }
      }
    )

  private def createMessages(count: Int): Seq[ReceivedMessage] =
    for (i <- 1 to count)
      yield
        ReceivedMessage.newBuilder
          .setMessage(PubSubMessage(s"id-$i").build())
          .setAckId(s"id-$i")
          .build()

  private def preparePubSub() = {
    val settings             = newTestSetup()
    val (_, _, subscription) = settings

    def data(index: Int) = s"test-$index"

    val messages = for (i <- 1 to 200) yield data(i)

    publishMessages(settings, messages: _*)

    subscription
  }
}

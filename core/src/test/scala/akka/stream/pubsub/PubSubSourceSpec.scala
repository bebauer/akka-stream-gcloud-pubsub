package akka.stream.pubsub

import java.util.UUID

import akka.actor.Status.Failure
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Terminated}
import akka.stream.ActorMaterializer
import akka.stream.pubsub.Subscriber.{FetchMessages, MessagesPulled}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.GraphStageLogic
import akka.stream.testkit.scaladsl.TestSink
import akka.testkit.{TestKit, TestProbe}
import com.google.protobuf.ByteString
import com.google.pubsub.v1.pubsub.{PubsubMessage, ReceivedMessage, Subscription, Topic}
import de.codecentric.akka.stream.gcloud.pubsub.client._
import io.grpc.{Status, StatusRuntimeException}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.util

class PubSubSourceSpec
    extends TestKit(ActorSystem("source-test"))
    with WordSpecLike
    with Eventually
    with Matchers
    with ScalaFutures
    with BeforeAndAfterAll {

  final val EmulatorUrl = "http://localhost:8085"

  override protected def afterAll(): Unit = system.terminate()

  "the source " should {
    "tell the client actor to pull a message " in {
      implicit val executionContext: ExecutionContextExecutor =
        scala.concurrent.ExecutionContext.global

      val subscription = preparePubSub()

      implicit val system = ActorSystem()

      try {
        implicit val mat = ActorMaterializer()

        val source =
          Source.fromGraph(PubSubSource(EmulatorUrl, subscription.fullName))

        val seq = source.take(200).runWith(Sink.seq)

        val messages = Await.result(seq, 10.seconds)

        messages.size shouldBe 200
      } finally {
        system.terminate()
      }
    }

    "recover from resource exhaustion" in {
      val subscriberProbe    = TestProbe()
      var stageRef: ActorRef = null

      val source = createSource(subscriberProbe.ref, stageRef = _)

      implicit val mat = ActorMaterializer()

      val seq = source.take(15).runWith(Sink.seq)

      subscriberProbe.expectMsg(FetchMessages(1000))
      subscriberProbe.send(stageRef, MessagesPulled(createMessages(10)))
      subscriberProbe.expectMsg(FetchMessages(1000))
      subscriberProbe.send(stageRef, Failure(new StatusRuntimeException(Status.RESOURCE_EXHAUSTED)))
      subscriberProbe.expectMsg(FetchMessages(1000))
      subscriberProbe.send(stageRef, MessagesPulled(createMessages(5)))

      val messages = Await.result(seq, 10.seconds)

      messages.size shouldBe 15
    }

    "terminate with failure on subscriber termination" in {
      val subscriberProbe    = TestProbe()
      var stageRef: ActorRef = null

      val source = createSource(subscriberProbe.ref, stageRef = _)

      implicit val mat = ActorMaterializer()

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

      implicit val mat = ActorMaterializer()

      val sub = source.take(1).runWith(TestSink.probe[ReceivedMessage])

      sub.request(1)

      subscriberProbe.expectMsg(FetchMessages(1000))
      subscriberProbe.send(stageRef, MessagesPulled(createMessages(10)))
      sub.expectNextPF {
        case msg: ReceivedMessage => msg.ackId shouldBe "id-1"
      }

      sub.expectComplete()
    }
  }

  private def createSource(subscriber: ActorRef, mappingCallback: (ActorRef => Unit)) =
    Source.fromGraph(new PubSubSource(EmulatorUrl, "subs") {
      override protected def createSubscriber(system: ActorSystem,
                                              stageActor: GraphStageLogic.StageActor,
                                              id: String): ActorRef = {
        mappingCallback(stageActor.ref)
        subscriber
      }
    })

  private def createMessages(count: Int): Seq[ReceivedMessage] =
    for (i <- 1 to count) yield ReceivedMessage(s"id-$i")

  private def preparePubSub()(implicit executionContext: ExecutionContextExecutor) = {
    val project      = ProjectName(s"test-${UUID.randomUUID().toString}")
    val topic        = TopicName(project, "top")
    val subscription = SubscriptionName(project, "subs")

    val client = PubSubClient(EmulatorUrl)

    Await.ready(client.createTopic(Topic(topic.fullName)), 10.seconds)
    Await.ready(client.createSubscription(Subscription(subscription.fullName, topic.fullName)),
                10.seconds)

    def data(index: Int) = ByteString.copyFromUtf8(s"test-$index")

    val messages = for (i <- 1 to 200) yield PubsubMessage(data(i))

    Await.ready(client.publish(topic.fullName, messages), 10.seconds)

    subscription
  }
}

package akka.stream.pubsub

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, PoisonPill, Terminated}
import akka.stream._
import akka.stream.pubsub.Acknowledger.{Acknowledge, Acknowledged}
import akka.stream.pubsub.PubSubAcknowledgeFlow.ReceivedMessages
import akka.stream.scaladsl.{Balance, Flow, GraphDSL, Merge}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.GraphStageLogic.StageActorRef.Receive
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.google.pubsub.v1.pubsub.ReceivedMessage
import de.codecentric.akka.stream.gcloud.pubsub.client.PubSubClient

import scala.annotation.tailrec
import scala.collection.immutable.Iterable

object PubSubAcknowledgeFlow {
  private type ReceivedMessages = Seq[ReceivedMessage]

  def apply(
      subscription: String,
      url: String = PubSubClient.DefaultPubSubUrl
  ): Flow[ReceivedMessages, ReceivedMessages, NotUsed] =
    Flow[ReceivedMessages].via(new PubSubAcknowledgeFlow(url, subscription))

  implicit class AcknowledgeFlowUtils(flow: Flow[ReceivedMessages, ReceivedMessages, NotUsed]) {
    def batched(size: Long = 50): Flow[ReceivedMessage, ReceivedMessage, NotUsed] =
      Flow[ReceivedMessage]
        .batch(size, seed => Seq(seed)) { (batch, element) =>
          batch :+ element
        }
        .via(flow)
        .mapConcat[ReceivedMessage] { messages =>
          messages.to[Iterable]
        }

    def parallel(count: Int): Flow[ReceivedMessages, ReceivedMessages, NotUsed] =
      Flow.fromGraph(GraphDSL.create() { implicit builder =>
        import GraphDSL.Implicits._

        val dispatch = builder.add(Balance[ReceivedMessages](count))
        val merge    = builder.add(Merge[ReceivedMessages](count))

        for (i <- 0 until count) {
          dispatch.out(i) ~> flow.async ~> merge.in(i)
        }

        FlowShape(dispatch.in, merge.out)
      })
  }
}

class PubSubAcknowledgeFlow(url: String, subscription: String)
    extends GraphStage[FlowShape[ReceivedMessages, ReceivedMessages]] {

  val in: Inlet[ReceivedMessages]   = Inlet[ReceivedMessages]("Acknowledge.in")
  val out: Outlet[ReceivedMessages] = Outlet[ReceivedMessages]("Acknowledge.out")

  override def shape: FlowShape[ReceivedMessages, ReceivedMessages] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private var self: StageActor                      = _
      private var acknowledger: ActorRef                = _
      private var outBuffer: Iterator[ReceivedMessages] = Iterator.empty
      private val id                                    = UUID.randomUUID().toString

      override def preStart(): Unit = {

        self = getStageActor(processing)

        acknowledger = {
          val system = ActorMaterializerHelper.downcast(materializer).system
          system.actorOf(Acknowledger.props(self.ref, url, subscription), Acknowledger.name(id, 0))
        }

        stageActor.watch(acknowledger)
      }

      def processing: Receive = {
        case (_, Acknowledged(messages)) =>
          processAcknowledges(messages)
        case (_, Terminated(ref)) if ref == acknowledger =>
          failStage(new Exception("Acknowledge actor terminated"))
      }

      def upstreamFinished: Receive = {
        case (_, Acknowledged(messages)) =>
          processAcknowledges(messages)
        case (_, Terminated(ref)) if ref == acknowledger =>
          completeStage()
      }

      def downstreamFinished: Receive = {
        case (_, Terminated(ref)) if ref == acknowledger =>
          completeStage()
      }

      private def processAcknowledges(messages: ReceivedMessages): Unit = {
        if (outBuffer.hasNext) {
          outBuffer = outBuffer ++ Iterator(messages)
        } else {
          outBuffer = Iterator(messages)
        }
        pump()
      }

      @tailrec
      def pump(): Unit =
        if (isAvailable(out)) {
          if (outBuffer.hasNext) {
            val msg = outBuffer.next()
            push(out, msg)
            pump()
          } else {
            if (!isClosed(in)) {
              pull(in)
            }
          }
        }

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit =
            acknowledger ! Acknowledge(grab(in))

          override def onUpstreamFinish(): Unit = {
            setKeepGoing(true)
            self.become(upstreamFinished)
            stopClientActor()
          }
        }
      )

      setHandler(
        out,
        new OutHandler {
          override def onPull(): Unit =
            pump()

          override def onDownstreamFinish(): Unit = {
            setKeepGoing(true)
            if (!isClosed(out)) {
              complete(out)
            }
            self.become(downstreamFinished)
            stopClientActor()
          }
        }
      )

      override def postStop(): Unit =
        stopClientActor()

      private def stopClientActor(): Unit =
        acknowledger ! PoisonPill
    }
}

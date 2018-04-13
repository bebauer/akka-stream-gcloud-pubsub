package akka.stream.pubsub

import java.util.UUID

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Terminated}
import akka.stream._
import akka.stream.pubsub.Acknowledger.{Acknowledge, Acknowledged}
import akka.stream.scaladsl.Flow
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.GraphStageLogic.StageActorRef.Receive
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings
import com.google.pubsub.v1.ProjectSubscriptionName
import com.typesafe.scalalogging.Logger
import gcloud.scala.pubsub._

import scala.annotation.tailrec

object PubSubAcknowledgeFlow {
  def apply(
      subscription: ProjectSubscriptionName,
      settings: SubscriberStubSettings = SubscriberStub.Settings()
  ): Flow[ReceivedMessages, ReceivedMessages, NotUsed] =
    Flow[ReceivedMessages].via(new PubSubAcknowledgeFlow(settings, subscription))
}

class PubSubAcknowledgeFlow(settings: SubscriberStubSettings, subscription: ProjectSubscriptionName)
    extends GraphStage[FlowShape[ReceivedMessages, ReceivedMessages]] {

  private val logger = Logger(classOf[PubSubAcknowledgeFlow])

  private val in: Inlet[ReceivedMessages]   = Inlet[ReceivedMessages]("Acknowledge.in")
  private val out: Outlet[ReceivedMessages] = Outlet[ReceivedMessages]("Acknowledge.out")

  override def shape: FlowShape[ReceivedMessages, ReceivedMessages] = FlowShape.of(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private var self: StageActor                      = _
      private var acknowledger: ActorRef                = _
      private var outBuffer: Iterator[ReceivedMessages] = Iterator.empty
      private val id                                    = UUID.randomUUID().toString
      private var system: ActorSystem                   = _

      logger.debug(s"creating acknowledge stage '$id'")

      override def preStart(): Unit = {

        self = getStageActor(processing)

        acknowledger = {
          system = ActorMaterializerHelper.downcast(materializer).system
          system.actorOf(Acknowledger
                           .props(self.ref, settings, subscription)
                           .withDispatcher("akka.stream.gcloud.pubsub.acknowledge.dispatcher"),
                         Acknowledger.name(id, 0))
        }

        stageActor.watch(acknowledger)

        logger.debug(s"created acknowledge actor: ${acknowledger.path}")
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
        logger.debug(s"processing acknowledged messages (${messages.size})")

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
            logger.trace(s"pushing message: $msg")
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
          override def onPush(): Unit = {
            val messages = grab(in)
            logger.debug(s"acknowledging grabbed messages: ${messages.size}")
            logger.trace(messages.toString())
            acknowledger ! Acknowledge(messages)
          }

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

      override def postStop(): Unit = {
        logger.debug("stopping acknowledge stage")
        system.stop(acknowledger)
      }

      private def stopClientActor(): Unit =
        acknowledger ! PoisonPill
    }
}

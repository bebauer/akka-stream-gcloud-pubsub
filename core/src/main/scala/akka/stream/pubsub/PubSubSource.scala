package akka.stream.pubsub

import java.util.UUID

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Terminated}
import akka.stream.pubsub.Subscriber.{FetchMessages, MessagesPulled}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{ActorMaterializerHelper, Attributes, Outlet, SourceShape}
import com.google.cloud.pubsub.v1.stub.SubscriberStubSettings
import com.google.pubsub.v1.{ProjectSubscriptionName, ReceivedMessage}
import com.typesafe.scalalogging.Logger
import gcloud.scala.pubsub._

object PubSubSource {

  private case object Pump

  def apply(subscriptionName: ProjectSubscriptionName,
            settings: SubscriberStubSettings = SubscriberStub.Settings()): PubSubSource =
    new PubSubSource(subscriptionName, settings)
}

class PubSubSource(subscriptionName: ProjectSubscriptionName, settings: SubscriberStubSettings)
    extends GraphStage[SourceShape[ReceivedMessage]]
    with Config {
  import PubSubSource._

  private val logger = Logger(classOf[PubSubSource])

  private val out: Outlet[ReceivedMessage] = Outlet("GooglePubSubSource.out")

  override val shape: SourceShape[ReceivedMessage] = SourceShape(out)

  protected def createSubscriber(system: ActorSystem,
                                 stageActor: StageActor,
                                 id: String): ActorRef =
    system.actorOf(
      Subscriber
        .props(stageActor.ref, settings, subscriptionName)
        .withDispatcher("akka.stream.gcloud.pubsub.source.dispatcher"),
      Subscriber.name(id, 0)
    )

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      private var self: StageActor                  = _
      private var subscriber: ActorRef              = _
      private var buffer: Iterator[ReceivedMessage] = Iterator.empty
      private val id                                = UUID.randomUUID().toString
      private var fetchCount                        = maxParallelFetchRequests
      private var system: ActorSystem               = _

      // since we do not know how many elements are in buffer without accessing it
      private var itemCount: Int = 0

      logger.debug(s"creating pubsub source '$id'")

      override def preStart(): Unit = {

        self = getStageActor(running)
        system = ActorMaterializerHelper.downcast(materializer).system

        subscriber = createSubscriber(system, self, id)

        stageActor.watch(subscriber)

        logger.debug(s"created subscriber actor: ${subscriber.path}")
      }

      def running: Receive = {
        case (_, MessagesPulled(messages)) =>
          logger.debug(s"messages pulled (${messages.size})")
          fetchCount += 1
          if (buffer.hasNext) {
            buffer = buffer ++ messages
            itemCount += messages.size
          } else {
            buffer = messages.toIterator
            itemCount = messages.size
          }
          pump()
        case (_, Terminated(ref)) if ref == subscriber =>
          failStage(new Exception("Subscriber actor terminated"))
        case (_, Pump) => pump()
      }

      def terminating: Receive = {
        case (_, Terminated(ref)) if ref == subscriber =>
          completeStage()
      }

      def pump(): Unit = {
        if (fetchCount > 0 && itemCount < fetchSize) {
          requestMessages()
        }
        if (isAvailable(out)) {
          if (buffer.hasNext) {
            val msg = buffer.next()
            itemCount -= 1
            logger.trace(s"push message: $msg")
            push(shape.out, msg)
            self.ref ! Pump
          }
        }
      }

      private def requestMessages(): Unit = {
        fetchCount -= 1
        subscriber ! FetchMessages(fetchSize)
        logger.debug(s"fetch messages ($fetchSize) - fetch count: $fetchCount")
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          pump()

        override def onDownstreamFinish(): Unit = performShutdown()
      })

      override def postStop(): Unit =
        system.stop(subscriber)

      private def stopClientActor(): Unit =
        subscriber ! PoisonPill

      private def performShutdown(): Unit = {
        setKeepGoing(true)

        if (!isClosed(shape.out)) {
          complete(shape.out)
        }

        self.become(terminating)
        stopClientActor()
      }
    }
}

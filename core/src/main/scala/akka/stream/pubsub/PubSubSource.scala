package akka.stream.pubsub

import java.util.UUID

import akka.actor.Actor.Receive
import akka.actor.Status.Failure
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Terminated}
import akka.stream.pubsub.Subscriber.{FetchMessages, MessagesPulled}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{ActorMaterializerHelper, Attributes, Outlet, SourceShape}
import com.google.pubsub.v1.pubsub.ReceivedMessage
import de.codecentric.akka.stream.gcloud.pubsub.client.{Config, PubSubClient}
import io.grpc.{Status, StatusRuntimeException}

object PubSubSource {

  case object Pump

  case object Resume

  def apply(subscription: String): PubSubSource =
    apply(PubSubClient.DefaultPubSubUrl, subscription)

  def apply(url: String, subscription: String): PubSubSource = new PubSubSource(url, subscription)
}

class PubSubSource(url: String, subscription: String)
    extends GraphStage[SourceShape[ReceivedMessage]]
    with Config {
  import PubSubSource._

  private val out: Outlet[ReceivedMessage] = Outlet("GooglePubSubSource.out")

  override val shape: SourceShape[ReceivedMessage] = SourceShape(out)

  protected def createSubscriber(system: ActorSystem,
                                 stageActor: StageActor,
                                 id: String): ActorRef =
    system.actorOf(Subscriber.props(stageActor.ref, url, subscription), Subscriber.name(id, 0))

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

      override def preStart(): Unit = {

        self = getStageActor(running)
        system = ActorMaterializerHelper.downcast(materializer).system

        subscriber = createSubscriber(system, self, id)

        stageActor.watch(subscriber)
      }

      def running: Receive = {
        case (_, Failure(ex: StatusRuntimeException))
            if ex.getStatus == Status.RESOURCE_EXHAUSTED =>
          self.become(waiting)
          system.scheduler.scheduleOnce(waitingTime, self.ref, Resume)(system.dispatcher)
        case (_, MessagesPulled(messages)) =>
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

      def waiting: Receive = {
        case (_, Resume) =>
          self.become(running)
          self.ref ! Pump
        case (_, MessagesPulled(messages)) =>
          fetchCount += 1
          if (buffer.hasNext) {
            buffer = buffer ++ messages
            itemCount += messages.size
          } else {
            buffer = messages.toIterator
            itemCount = messages.size
          }
        case (_, Terminated(ref)) if ref == subscriber =>
          failStage(new Exception("Subscriber actor terminated"))
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
            push(shape.out, msg)
            self.ref ! Pump
          }
        }
      }

      private def requestMessages(): Unit = {
        fetchCount -= 1
        subscriber ! FetchMessages(fetchSize)
      }

      setHandler(out, new OutHandler {
        override def onPull(): Unit =
          pump()

        override def onDownstreamFinish(): Unit = performShutdown()
      })

      override def postStop(): Unit =
        stopClientActor()

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

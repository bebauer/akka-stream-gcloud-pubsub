package akka.stream.pubsub

import java.util.UUID

import akka.actor.Actor.Receive
import akka.actor.{ActorRef, ActorSystem, PoisonPill, Terminated}
import akka.stream._
import akka.stream.pubsub.Publisher.{Publish, Published}
import akka.stream.stage.GraphStageLogic.StageActor
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import com.google.cloud.pubsub.v1.stub.PublisherStubSettings
import com.google.pubsub.v1.ProjectTopicName
import com.typesafe.scalalogging.Logger
import gcloud.scala.pubsub._

import scala.concurrent.ExecutionContextExecutor

object PubSubPublishFlow {
  def apply(topicName: ProjectTopicName,
            settings: PublisherStubSettings = PublisherStub.Settings()): PubSubPublishFlow =
    new PubSubPublishFlow(topicName, settings)
}

class PubSubPublishFlow(topicName: ProjectTopicName, settings: PublisherStubSettings)
    extends GraphStage[FlowShape[PublishMessages, PublishedIds]] {

  private val logger = Logger(classOf[PubSubPublishFlow])

  private val in: Inlet[PublishMessages] = Inlet("GooglePubSubPublishFlow.in")

  private val out: Outlet[PublishedIds] = Outlet("GooglePubSubPublishFlow.out")

  override val shape: FlowShape[PublishMessages, PublishedIds] = FlowShape(in, out)

  protected def createPublisher(system: ActorSystem,
                                stageActor: GraphStageLogic.StageActor,
                                id: String): ActorRef = system.actorOf(
    Publisher
      .props(stageActor.ref, settings, topicName)
      .withDispatcher("akka.stream.gcloud.pubsub.publish.dispatcher"),
    Subscriber.name(id, 0)
  )

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      implicit val executor: ExecutionContextExecutor = scala.concurrent.ExecutionContext.global

      private var self: StageActor    = _
      private var publisher: ActorRef = _
      private val id                  = UUID.randomUUID().toString
      private var system: ActorSystem = _

      logger.debug(s"creating publish stage '$id")

      override def preStart(): Unit = {
        self = getStageActor(running)
        system = ActorMaterializerHelper.downcast(materializer).system

        publisher = createPublisher(system, self, id)

        stageActor.watch(publisher)

        logger.debug(s"created publisher actor: ${publisher.path}")
      }

      def running: Receive = {
        case (_, Published(ids)) =>
          logger.trace(s"published messages: ${ids.size}")
          push(out, ids)
        case (_, Terminated(ref)) if ref == publisher =>
          failStage(new Exception("Publisher actor terminated"))
      }

      def upstreamFinished: Receive = {
        case (_, Published(ids)) =>
          logger.trace(s"published messages: ${ids.size}")
          push(out, ids)
        case (_, Terminated(ref)) if ref == publisher =>
          completeStage()
      }

      def downstreamFinished: Receive = {
        case (_, Terminated(ref)) if ref == publisher =>
          completeStage()
      }

      setHandler(
        in,
        new InHandler {
          override def onPush(): Unit = {
            val messages = grab(in)
            logger.debug(s"publishing grabbed messages: ${messages.size}")
            logger.trace(messages.toString())
            publisher ! Publish(messages)
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
          override def onPull(): Unit = pull(in)

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
        system.stop(publisher)

      private def stopClientActor(): Unit =
        publisher ! PoisonPill
    }
}

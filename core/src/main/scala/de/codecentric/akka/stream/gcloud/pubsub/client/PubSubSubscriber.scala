package de.codecentric.akka.stream.gcloud.pubsub.client

import akka.stream.pubsub.ReceivedMessages
import com.google.auth.Credentials
import com.google.protobuf.empty.Empty
import com.google.pubsub.v1.pubsub._
import io.grpc.Channel
import io.grpc.auth.MoreCallCredentials

import scala.concurrent.{ExecutionContext, Future}

trait PubSubSubscriber {
  implicit val executionContext: ExecutionContext

  def getChannel: Channel
  def getCredentials: Credentials

  private def subscriberStub = SubscriberGrpc
    .stub(getChannel)
    .withCallCredentials(MoreCallCredentials.from(getCredentials))

  def getSubscription(request: GetSubscriptionRequest): Future[Option[Subscription]] =
    subscriberStub
      .listSubscriptions(
        ListSubscriptionsRequest(
          project = SubscriptionName(request.subscription).projectName.fullName
        )
      )
      .map(response => response.subscriptions.find(_.name == request.subscription))

  def createSubscription(subscription: Subscription): Future[Subscription] =
    subscriberStub
      .createSubscription(subscription)

  def pull(request: PullRequest): Future[ReceivedMessages] =
    subscriberStub
      .pull(request)
      .map { response =>
        response.receivedMessages.to[Seq]
      }

  def acknowledge(request: AcknowledgeRequest): Future[Empty] =
    subscriberStub.acknowledge(request)
}

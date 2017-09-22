# Akka Streams Google Cloud Pub/Sub

A akka streams source and acknowledge flow for Google's Cloud Pub/Sub. 
It uses GRPC for communication with Pub/Sub.

## Installation

```scala
"de.codecentric" %% "akka-stream-gcloud-pubsub" % "0.0.1"
```

## Usage

There two classes to be used. `PubSubSource` and `PubSubAcknowledgeFlow`.
The source pulls messages from Pub/Sub and pushes them onto the stream.
The acknowledge flow acknowledges all incoming messages.

### PubSubSource

Creating the source:

```scala
// with default Pub/Sub URL
Source.fromGraph(PubSubSource(subscriptionName))

// with custom URL
Source.fromGraph(PubSubSource(pubSubUrl, subscriptionName))
```

Every `PubSubSource` maintains a single connection to Pub/Sub, 
so for more throughput multiple sources can be combined.

The `subscriptionName` must be the fully qualified name of the subscription:
`projects/<project>/subscriptions/<subscription>`

### PubSubAcknowledgeFlow

Create an acknowledge flow, which acknowledges a sequence of messages:

```scala
// with default Pub/Sub URL
PubSubAcknowledgeFlow(subscriptionName)

// with custom URL
PubSubAcknowledgeFlow(subscriptionName, pubSubUrl)
```

The `subscriptionName` must be the fully qualified name of the subscription:
`projects/<project>/subscriptions/<subscription>`

Create a flow the acknowledges messages parallel:

```scala
import PubSubAcknowledgeFlow._

PubSubAcknowledgeFlow(subscriptionName).parallel(count)
```

Create a flow the batches single messages before acknowledging:

```scala
import PubSubAcknowledgeFlow._

PubSubAcknowledgeFlow(subscriptionName).batched(size)
```

## Configuration

There are several configuration options which can be set.

**akka.stream.gcloud.pubsub.source.fetchSize**

Defines how many messages are requested from Pub/Sub at once. 
The default is `1000`.

**akka.stream.gcloud.pubsub.source.maxInboundMessageSize**

Maximum GRPC frame size for incoming messages. 
The default value is `1000000`.

**akka.stream.gcloud.pubsub.source.maxParallelFetchRequests**

Maximum amount of parallel fetch requests from the `PubSubSource` in case of high demand. 
The default value is `10`.

**akka.stream.gcloud.pubsub.source.waitingTime**

The time the source waits for further requests when it gets a resource exhaustion error from Pub/Sub.
The default value is `500ms`.
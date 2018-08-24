# Akka Streams Google Cloud Pub/Sub

[![Download](https://api.bintray.com/packages/bebauer/maven/akka-stream-gcloud-pubsub/images/download.svg) ](https://bintray.com/bebauer/maven/akka-stream-gcloud-pubsub/_latestVersion)

An akka streams solution for Google's Cloud Pub/Sub. It supports stages for
pulling, acknownleding and publishing messages.

It uses [gcloud-scala](https://github.com/bebauer/gcloud-scala) to communicate 
with Pub/Sub and configure the stages.

## Usage

```
// Add resolver for https://dl.bintray.com/bebauer/maven/
resolvers += Resolver.bintrayRepo("bebauer", "maven")

// Add dependency
"de.codecentric" %% "akka-stream-gcloud-pubsub" % "0.2.0"
```

The provided stages are `PubSubSource`, `PubSubAcknowledgeFlow` and 
`PubSubPublishFlow`.

The source pulls messages from Pub/Sub and pushes them onto the stream.

The acknowledge flow acknowledges all incoming messages.

The publish flow publishes all incoming messages.

### PubSubSource

Creating the source:

```
// with default settings
Source.fromGraph(PubSubSource(subscriptionName))

// with custom settings
Source.fromGraph(PubSubSource(subscriptionName, settings))
```

Every `PubSubSource` maintains a single connection to Pub/Sub, 
so for more throughput multiple sources can be combined.

### PubSubAcknowledgeFlow

Create an acknowledge flow, which acknowledges a sequence of messages:

```
// with default settings
PubSubAcknowledgeFlow(subscriptionName)

// with custom settings
PubSubAcknowledgeFlow(subscriptionName, settings)
```

### PubSubPublishFlow

Create a publish flow, which publishes a sequence of messages to Pub/Sub:

```
// with default settings
PubSubPublishFlow(topicName)

// with custom settings
PubSubPublishFlow(topicName, settings)
```

## Configuration

There are several configuration options which can be set.

**akka.stream.gcloud.pubsub.source.fetchSize**

Defines how many messages are requested from Pub/Sub at once. 
The default is `1000`.

**akka.stream.gcloud.pubsub.source.maxParallelFetchRequests**

Maximum amount of parallel fetch requests from the `PubSubSource` in case of high demand. 
The default value is `10`.

## Example Stream

```
import gcloud.scala.pubsub._

val source = Source.fromGraph(PubSubSource("projects/xxx/subscriptions/xxx"))

val ackFlow = PubSubAcknowledgeFlow("projects/xxx/subscriptions/xxx")

val publishFlow = PubSubPublishFlow("projects/xxx/topics/xxx")

source.groupedWithin(500, 100.milliseconds)
      .via(ackFlow)
      .map(_.map(receivedToPubSubMessage))
      .via(publishFlow)
      .toMat(Sink.ignore)
      .run()
```

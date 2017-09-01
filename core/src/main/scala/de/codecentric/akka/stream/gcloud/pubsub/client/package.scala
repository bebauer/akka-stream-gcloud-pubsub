package de.codecentric.akka.stream.gcloud.pubsub

package object client {

  object ProjectName {
    private val ProjectNamePattern = "(projects/)?(.+)".r

    def apply(name: String): ProjectName =
      new ProjectName(name match {
        case ProjectNamePattern(n)    => n
        case ProjectNamePattern(_, n) => n
      })
  }

  class ProjectName(val name: String) {
    val fullName: String = s"projects/$name"
  }

  object TopicName {
    private val TopicNamePattern = "projects/(.+)/topics/(.+)".r

    def apply(name: String): TopicName = name match {
      case TopicNamePattern(project, topic) => TopicName(project, topic)
    }

    def apply(projectName: String, name: String): TopicName =
      TopicName(ProjectName(projectName), name)

    def apply(projectName: ProjectName, name: String): TopicName = new TopicName(projectName, name)
  }

  class TopicName(val projectName: ProjectName, val name: String) {
    val fullName: String = s"${projectName.fullName}/topics/$name"
  }

  object SubscriptionName {
    private val SubscriptionNamePattern = "projects/(.+)/subscriptions/(.+)".r

    def apply(name: String): SubscriptionName = name match {
      case SubscriptionNamePattern(project, subscription) =>
        SubscriptionName(project, subscription)
    }

    def apply(projectName: String, name: String): SubscriptionName =
      SubscriptionName(ProjectName(projectName), name)

    def apply(projectName: ProjectName, name: String): SubscriptionName =
      new SubscriptionName(projectName, name)
  }

  class SubscriptionName(val projectName: ProjectName, val name: String) {
    val fullName: String = s"${projectName.fullName}/subscriptions/$name"
  }
}



lazy val `akka-stream-gcloud-pubsub` = project
  .in(file("."))
  .enablePlugins(GitVersioning)
  .settings(commonSettings)
  .settings(gitSettings)
  .settings(libraryDependencies ++= Seq(
    library.AkkaActor,
    library.AkkaStream,
    library.AkkaSlf4j,
    library.Slf4jApi,
    library.LogbackClassic,
    library.AkkaTestkit % Test,
    library.AkkaStreamTestkit % Test)
  )


lazy val commonSettings = Seq(
  name := "akka-stream-gcloud-pubsub",
  organization := "de.codecentric",
  scalaVersion := "2.11.8",
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-target:jvm-1.8",
    "-encoding", "UTF-8"
  ),
  javacOptions ++= Seq(
    "-source", "1.8",
    "-target", "1.8"
  )
)

lazy val gitSettings = Seq(
  git.useGitDescribe := true
)

lazy val library = new {

  object Version {
    val Akka = "2.4.17"
    val Slf4j = "1.7.25"
    val Logback = "1.2.2"
  }

  val AkkaActor = "com.typesafe.akka" %% "akka-actor" % Version.Akka
  val AkkaStream = "com.typesafe.akka" %% "akka-stream" % Version.Akka
  val AkkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % Version.Akka
  val Slf4jApi = "org.slf4j" % "slf4j-api" % Version.Slf4j
  val LogbackClassic = "ch.qos.logback" % "logback-classic" % Version.Logback
  val AkkaTestkit = "com.typesafe.akka" %% "akka-testkit" % Version.Akka
  val AkkaStreamTestkit = "com.typesafe.akka" %% "akka-stream-testkit" % Version.Akka
}





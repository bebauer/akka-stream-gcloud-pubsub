lazy val `akka-stream-gcloud-pubsub` = project
  .in(file("."))
  .settings(commonSettings)
  .settings(publish := {}, publishLocal := {})
  .aggregate(core)
  .aggregate(benchmark)

lazy val core = project
  .in(file("core"))
  .settings(name := "akka-stream-gcloud-pubsub")
  .settings(commonSettings)
  .settings(
    libraryDependencies ++= Seq(
      library.AkkaActor,
      library.AkkaStream,
      library.AkkaSlf4j,
      library.Slf4jApi,
      library.ScalaLogging,
      library.GCloudScalaPubSub,
      library.LogbackClassic           % Test,
      library.AkkaTestkit              % Test,
      library.AkkaStreamTestkit        % Test,
      library.ScalaTest                % Test,
      library.GCloudScalaPubSubTestkit % Test
    )
  )

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(name := "akka-stream-gcloud-pubsub-benchmark")
  .settings(commonSettings)
  .settings(
    assemblyJarName in assembly := "benchmark.jar",
    mainClass in assembly := Some("akka.stream.gcloud.pubsub.benchmark.Main"),
    publish := {},
    publishLocal := {}
  )
  .settings(
    libraryDependencies ++= Seq(
      library.AkkaHttp,
      library.AkkaHttpCirce,
      library.CirceCore,
      library.CirceGeneric,
      library.CirceParser,
      library.Metrics,
      library.LogbackClassic
    )
  )
  .dependsOn(core)

lazy val commonSettings = Seq(
  organization := "de.codecentric",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),
  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-language:_",
    "-target:jvm-1.8",
    "-encoding",
    "UTF-8"
  ),
  javacOptions ++= Seq(
    "-source",
    "1.8",
    "-target",
    "1.8"
  ),
  fork in Test := true,
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case "META-INF/io.netty.versions.properties" => MergeStrategy.discard
    case x =>
      val oldStrategy = (assemblyMergeStrategy in assembly).value
      oldStrategy(x)
  },
  resolvers += Resolver.bintrayRepo("bebauer", "maven"),
  licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0"))
)

lazy val library = new {

  object Version {
    val Akka                = "2.5.11"
    val AkkaHttp            = "10.1.0"
    val AkkaHttpCirce       = "1.16.1"
    val Slf4j               = "1.7.25"
    val Logback             = "1.2.2"
    val ScalaTest           = "3.0.1"
    val CirceVersion        = "0.8.0"
    val MetricsVersion      = "3.5.8_a2.4"
    val GCloudScalaVersion  = "0.10.0"
    val ScalaLoggingVersion = "3.9.0"
  }

  val AkkaActor                = "com.typesafe.akka"          %% "akka-actor"                  % Version.Akka
  val AkkaStream               = "com.typesafe.akka"          %% "akka-stream"                 % Version.Akka
  val AkkaSlf4j                = "com.typesafe.akka"          %% "akka-slf4j"                  % Version.Akka
  val AkkaHttp                 = "com.typesafe.akka"          %% "akka-http"                   % Version.AkkaHttp
  val AkkaHttpCirce            = "de.heikoseeberger"          %% "akka-http-circe"             % Version.AkkaHttpCirce
  val CirceCore                = "io.circe"                   %% "circe-core"                  % Version.CirceVersion
  val CirceGeneric             = "io.circe"                   %% "circe-generic"               % Version.CirceVersion
  val CirceParser              = "io.circe"                   %% "circe-parser"                % Version.CirceVersion
  val Slf4jApi                 = "org.slf4j"                  % "slf4j-api"                    % Version.Slf4j
  val LogbackClassic           = "ch.qos.logback"             % "logback-classic"              % Version.Logback
  val AkkaTestkit              = "com.typesafe.akka"          %% "akka-testkit"                % Version.Akka
  val AkkaStreamTestkit        = "com.typesafe.akka"          %% "akka-stream-testkit"         % Version.Akka
  val ScalaTest                = "org.scalatest"              %% "scalatest"                   % Version.ScalaTest
  val Metrics                  = "nl.grons"                   %% "metrics-scala"               % Version.MetricsVersion
  val GCloudScalaPubSub        = "gcloud-scala"               %% "gcloud-scala-pubsub"         % Version.GCloudScalaVersion
  val GCloudScalaPubSubTestkit = "gcloud-scala"               %% "gcloud-scala-pubsub-testkit" % Version.GCloudScalaVersion
  val ScalaLogging             = "com.typesafe.scala-logging" %% "scala-logging"               % Version.ScalaLoggingVersion
}

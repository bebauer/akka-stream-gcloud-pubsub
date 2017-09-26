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
  .settings(publishSettings)
  .settings(
    libraryDependencies ++= Seq(
      library.AkkaActor,
      library.AkkaStream,
      library.AkkaSlf4j,
      library.Slf4jApi,
      library.LogbackClassic,
      library.NettyTcNative,
      library.GrpcNetty,
      library.GrpcStub,
      library.GrpcAuth,
      library.GrpcProtobuf,
      library.GoogleOauth2,
      library.GfcGuava,
      library.ScalaPbRuntimeGrpc % "protobuf, compile",
      library.AkkaTestkit        % Test,
      library.AkkaStreamTestkit  % Test,
      library.Scalactic          % Test,
      library.ScalaTest          % Test
    )
  )
  .settings(
    PB.targets in Compile in ThisBuild := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )

lazy val benchmark = project
  .in(file("benchmark"))
  .settings(name := "akka-stream-gcloud-pubsub-benchmark")
  .settings(commonSettings)
  .settings(
    assemblyJarName in assembly := "benchmark.jar",
    mainClass in assembly := Some("de.codecentric.akka.stream.gcloud.pubsub.benchmark.Main"),
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
      library.Metrics
    )
  )
  .dependsOn(core)

lazy val commonSettings = Seq(
  organization := "de.codecentric",
  scalaVersion := "2.12.2",
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
  }
)

lazy val sbtCredentialFile =
  Seq(file("/var/lib/jenkins/secrets/crm360-credentials"),
      Path.userHome / ".ivy2" / ".crm360-credentials").find(_.exists())

lazy val publishSettings = Seq(
  sbtCredentialFile.map(credentials += Credentials(_)).getOrElse(credentials ++= Seq()),
  publishTo := {
    val artifactory = "https://artifactory.crm.conrad.com/artifactory/"
    Some("releases" at artifactory + "maven-releases")
  }
)

lazy val library = new {

  object Version {
    val Akka               = "2.5.4"
    val AkkaHttp           = "10.0.10"
    val AkkaHttpCirce      = "1.16.1"
    val Slf4j              = "1.7.25"
    val Logback            = "1.2.2"
    val Grpc               = "1.4.0"
    val ScalaTest          = "3.0.1"
    val NettyTcNative      = "2.0.3.Final"
    val GoogleOauth2       = "0.7.0"
    val GfcGuava           = "0.2.5"
    val ScalaPbRuntimeGrpc = "0.6.2"
    val CirceVersion       = "0.8.0"
    val MetricsVersion     = "3.5.8_a2.4"
  }

  val AkkaActor          = "com.typesafe.akka"      %% "akka-actor"                     % Version.Akka
  val AkkaStream         = "com.typesafe.akka"      %% "akka-stream"                    % Version.Akka
  val AkkaSlf4j          = "com.typesafe.akka"      %% "akka-slf4j"                     % Version.Akka
  val AkkaHttp           = "com.typesafe.akka"      %% "akka-http"                      % Version.AkkaHttp
  val AkkaHttpCirce      = "de.heikoseeberger"      %% "akka-http-circe"                % Version.AkkaHttpCirce
  val CirceCore          = "io.circe"               %% "circe-core"                     % Version.CirceVersion
  val CirceGeneric       = "io.circe"               %% "circe-generic"                  % Version.CirceVersion
  val CirceParser        = "io.circe"               %% "circe-parser"                   % Version.CirceVersion
  val Slf4jApi           = "org.slf4j"              % "slf4j-api"                       % Version.Slf4j
  val LogbackClassic     = "ch.qos.logback"         % "logback-classic"                 % Version.Logback
  val AkkaTestkit        = "com.typesafe.akka"      %% "akka-testkit"                   % Version.Akka
  val AkkaStreamTestkit  = "com.typesafe.akka"      %% "akka-stream-testkit"            % Version.Akka
  val GrpcNetty          = "io.grpc"                % "grpc-netty"                      % Version.Grpc
  val GrpcStub           = "io.grpc"                % "grpc-stub"                       % Version.Grpc
  val GrpcAuth           = "io.grpc"                % "grpc-auth"                       % Version.Grpc
  val GrpcProtobuf       = "io.grpc"                % "grpc-protobuf"                   % Version.Grpc
  val NettyTcNative      = "io.netty"               % "netty-tcnative-boringssl-static" % Version.NettyTcNative
  val GoogleOauth2       = "com.google.auth"        % "google-auth-library-oauth2-http" % Version.GoogleOauth2
  val GfcGuava           = "com.gilt"               %% "gfc-guava"                      % Version.GfcGuava
  val Scalactic          = "org.scalactic"          %% "scalactic"                      % Version.ScalaTest
  val ScalaTest          = "org.scalatest"          %% "scalatest"                      % Version.ScalaTest
  val ScalaPbRuntimeGrpc = "com.trueaccord.scalapb" %% "scalapb-runtime-grpc"           % Version.ScalaPbRuntimeGrpc
  val Metrics            = "nl.grons"               %% "metrics-scala"                  % Version.MetricsVersion
}

addSbtPlugin("io.get-coursier"   % "sbt-coursier" % "1.0.0-RC10")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt" % "1.2.0")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "2.0.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header"   % "3.0.1")

addSbtPlugin("com.thesamet" % "sbt-protoc" % "0.99.12-rc5")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.5")

libraryDependencies ++= Seq(
  "com.trueaccord.scalapb" %% "compilerplugin" % "0.6.1",
  "org.slf4j"              % "slf4j-nop"       % "1.7.25"
)

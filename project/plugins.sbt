addSbtPlugin("io.get-coursier"   % "sbt-coursier" % "1.0.0-RC10")
addSbtPlugin("com.geirsson"      % "sbt-scalafmt" % "1.2.0")
addSbtPlugin("com.dwijnand"      % "sbt-dynver"   % "2.0.0")
addSbtPlugin("de.heikoseeberger" % "sbt-header"   % "3.0.1")
addSbtPlugin("org.foundweekends" % "sbt-bintray"  % "0.5.1")
addSbtPlugin("com.eed3si9n"      % "sbt-assembly" % "0.14.5")

libraryDependencies ++= Seq(
  "org.slf4j" % "slf4j-nop" % "1.7.25"
)

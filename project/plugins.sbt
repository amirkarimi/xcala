// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.8.20")

// web plugins
addSbtPlugin("com.typesafe.sbt"        % "sbt-coffeescript" % "1.0.2")
addSbtPlugin("com.typesafe.sbt"        % "sbt-less"         % "1.1.2")
addSbtPlugin("com.typesafe.sbt"        % "sbt-rjs"          % "1.0.10")
addSbtPlugin("com.typesafe.sbt"        % "sbt-digest"       % "1.1.3")
addSbtPlugin("com.typesafe.sbt"        % "sbt-mocha"        % "1.1.2")
addSbtPlugin("com.typesafe.sbt"        % "sbt-gzip"         % "1.0.2")
addSbtPlugin("ch.epfl.scala"           % "sbt-scalafix"     % "0.11.0")
addSbtPlugin("com.sksamuel.scapegoat" %% "sbt-scapegoat"    % "1.1.1")

// This fixes scala-xml version conflict in plugin dependencies
ThisBuild / libraryDependencySchemes ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
)

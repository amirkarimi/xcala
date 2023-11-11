name := """xcala.play"""

organization := "com.xcala"

version := "0.31.4"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.12"

resolvers ++= Seq(
)

publishTo := Some(
  "Sonatype Nexus Repository Manager".at("https://nexus.darkube.app/repository/ajor-maven/")
)

credentials += Credentials(
  "Sonatype Nexus Repository Manager",
  "nexus.darkube.app",
  "ci",
  System.getenv.get("NEXUS_KEY")
)

libraryDependencies ++= Seq(
  guice,
  ws,
  ehcache,
  filters,
  jodaForms,
  "org.reactivemongo"            %% "reactivemongo"              % "1.0.10",
  "com.nappin"                   %% "play-recaptcha"             % "2.5",
  "com.typesafe.akka"            %% "akka-actor-typed"           % "2.8.5",
  "com.typesafe.akka"            %% "akka-testkit"               % "2.8.5" % "test",
  "com.typesafe.akka"            %% "akka-serialization-jackson" % "2.8.5",
  "com.typesafe.akka"            %% "akka-stream"                % "2.8.5",
  "com.typesafe.akka"            %% "akka-slf4j"                 % "2.8.5",
  "com.bahmanm"                  %% "persianutils"               % "4.0",
  "io.lemonlabs"                 %% "scala-uri"                  % "4.0.3",
  "org.apache.tika"               % "tika-core"                  % "2.9.0",
  "ch.qos.logback"                % "logback-classic"            % "1.4.8", // watchout for 1.4.9+ changes
  "io.sentry"                     % "sentry-logback"             % "6.29.0",
  "io.minio"                      % "minio"                      % "8.5.6",
  "commons-io"                    % "commons-io"                 % "2.14.0",
  "com.sksamuel.scrimage"        %% "scrimage-scala"             % "4.1.0",
  "com.sksamuel.scrimage"         % "scrimage-webp"              % "4.1.0",
  "com.fasterxml.jackson.module" %% "jackson-module-scala"       % "2.15.2",
  "org.postgresql"                % "postgresql"                 % "42.6.0",
  "com.typesafe.play"            %% "play-slick"                 % "5.1.0",
  "com.github.tototoshi"         %% "slick-joda-mapper"          % "2.8.0",
  "com.github.tminglei"          %% "slick-pg"                   % "0.21.1",
  "com.lightbend.akka"           %% "akka-stream-alpakka-slick"  % "6.0.1",
  "com.ibm.icu"                   % "icu4j"                      % "73.2",
  specs2                          % Test
)

ThisBuild / scapegoatVersion := "2.1.3"

scapegoatIgnoredFiles := Seq(
  ".*/ReverseRoutes.scala",
  ".*/JavaScriptReverseRoutes.scala",
  ".*/Routes.scala"
)

scapegoatDisabledInspections := Seq(
  "DuplicateImport",
  "CatchThrowable",
  "UnusedMethodParameter",
  "OptionGet",
  "BooleanParameter",
  "VariableShadowing",
  "UnsafeTraversableMethods",
  "CatchException",
  "EitherGet",
  "ComparingFloatingPointTypes",
  "PartialFunctionInsteadOfMatch",
  "AsInstanceOf",
  "ClassNames"
)

Assets / LessKeys.less / includeFilter := "*.less"
Assets / LessKeys.less / excludeFilter := "_*.less"

pipelineStages := Seq(gzip)

routesImport ++= Seq(
  "reactivemongo.api.bson.BSONObjectID",
  "xcala.play.extensions.Bindables._",
  "play.api.i18n.Messsages"
)

TwirlKeys.templateImports ++= Seq(
  "reactivemongo.api.bson.{BSONObjectID, BSONDocument}",
  "_root_.xcala.play.models._",
  "reactivemongo.api.gridfs.ReadFile",
  "_root_.xcala.play.extensions.PersianUtils._",
  "_root_.xcala.play.extensions.MultilangHelper._"
)

scalacOptions ++= Seq(
  "-feature",
  "-Wunused",
  "-Wdead-code",
  "-Xlint",
  "-Wconf:cat=unused-imports&site=.*views.html.*:s", // Silence import warnings in Play html files
  "-Wconf:cat=unused-imports&site=<empty>:s",        // Silence import warnings on Play `routes` files
  "-Wconf:cat=unused-imports&site=router:s"          // Silence import warnings on Play `routes` files
)

publishConfiguration      := publishConfiguration.value.withOverwrite(true)
publishLocalConfiguration := publishLocalConfiguration.value.withOverwrite(true)

Test / javaOptions ++= Seq("--add-opens=java.base/java.lang=ALL-UNNAMED")

ThisBuild / scalafixScalaBinaryVersion :=
  CrossVersion.binaryScalaVersion(scalaVersion.value)

addCompilerPlugin("org.scalameta" % "semanticdb-scalac" % "4.8.10" cross CrossVersion.full)

scalacOptions ++= List(
  "-Yrangepos",
  "-P:semanticdb:synthetics:on"
)

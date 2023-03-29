name := """xcala.play"""

organization := "com.xcala"

version := "0.21.4"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.13.10"

resolvers ++= Seq(
  "Atlassian Releases".at("https://maven.atlassian.com/public/"),
  "Sonatype Snapshots".at("https://oss.sonatype.org/content/repositories/snapshots/"),
  "scalaz-bintray".at("https://dl.bintray.com/scalaz/releases")
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
  "org.reactivemongo"     %% "reactivemongo"              % "1.0.10",
  "com.nappin"            %% "play-recaptcha"             % "2.5",
  "com.typesafe.akka"     %% "akka-actor-typed"           % "2.7.0",
  "com.typesafe.akka"     %% "akka-testkit"               % "2.7.0" % "test",
  "com.typesafe.akka"     %% "akka-serialization-jackson" % "2.7.0",
  "com.typesafe.akka"     %% "akka-stream"                % "2.7.0",
  "com.typesafe.akka"     %% "akka-slf4j"                 % "2.7.0",
  "com.bahmanm"           %% "persianutils"               % "4.0",
  "io.lemonlabs"          %% "scala-uri"                  % "4.0.3",
  "org.apache.tika"        % "tika-core"                  % "2.7.0",
  "ch.qos.logback"         % "logback-classic"            % "1.4.6",
  "io.sentry"              % "sentry-logback"             % "6.16.0",
  "io.minio"               % "minio"                      % "8.5.2",
  "commons-io"             % "commons-io"                 % "2.11.0",
  "com.sksamuel.scrimage" %% "scrimage-scala"             % "4.0.34",
  "com.sksamuel.scrimage"  % "scrimage-webp"              % "4.0.34",
  specs2                   % Test
)

ThisBuild / scapegoatVersion := "2.1.1"

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

(Compile / compile) := Def.taskDyn {
  val c = (Compile / compile).value
  Def.task {
    (Compile / scapegoat).toTask.value
    c
  }
}.value

(Test / compile) := Def.taskDyn {
  val c = (Test / compile).value
  Def.task {
    (Test / scapegoat).toTask.value
    c
  }
}.value

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

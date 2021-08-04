name := """xcala.play"""

organization := "com.xcala"

version := "0.3"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.13"

resolvers ++= Seq(
  "Atlassian Releases" at "https://maven.atlassian.com/public/",
  "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
)

libraryDependencies ++= Seq(
  ws,
  ehcache,
  filters,
  "org.reactivemongo" %% "reactivemongo" % "1.0.5",
  "org.reactivemongo" %% "play2-reactivemongo" % "1.0.5-play27",
  "com.typesafe.play" %% "play-iteratees" % "2.6.1",
  "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1",
  "com.softwaremill.macwire" %% "macros" % "2.4.0",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "net.sf.jsignature.io-tools" % "easystream" % "1.2.12",
  "com.sksamuel.scrimage" %% "scrimage-core" % "2.1.8",
  "com.bahmanm" %% "persianutils" % "3.0",
  "net.tanesha.recaptcha4j" % "recaptcha4j" % "0.0.7",
  "com.typesafe.play" %% "play-mailer" % "7.0.1",
  "io.lemonlabs" %% "scala-uri" % "1.5.1"
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

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

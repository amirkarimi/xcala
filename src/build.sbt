import play.PlayImport.PlayKeys._

name := """xcala.play"""

organization := "com.xcala"

version := "0.1.2"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

resolvers += "Sonatype Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  cache,
  ws,
  filters,
  "org.reactivemongo" %% "reactivemongo" % "0.10.5.0.akka23",
  "org.reactivemongo" %% "play2-reactivemongo" % "0.10.5.0.akka23",
  "com.softwaremill.macwire" %% "macros" % "0.7.3",
  "org.mindrot" % "jbcrypt" % "0.3m",
  "net.sf.jsignature.io-tools" % "easystream" % "1.2.12",
  "com.sksamuel.scrimage" %% "scrimage-core" % "1.4.1",
  "com.bahmanm" %% "persianutils" % "1.0",
  "net.tanesha.recaptcha4j" % "recaptcha4j" % "0.0.7",
  "com.typesafe.play.plugins" %% "play-plugins-mailer" % "2.3.0",
  "com.netaporter" %% "scala-uri" % "0.4.4"
)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"

pipelineStages := Seq(gzip)

routesImport += "reactivemongo.bson.BSONObjectID"

routesImport += "xcala.play.extensions.Bindables._"

routesImport += "play.api.i18n.Lang"

TwirlKeys.templateImports += "reactivemongo.bson.{BSONObjectID, BSONDocument}"

TwirlKeys.templateImports += "_root_.xcala.play.models._"

TwirlKeys.templateImports += "reactivemongo.api.gridfs.ReadFile"

TwirlKeys.templateImports += "_root_.xcala.play.extensions.PersianUtils._"

TwirlKeys.templateImports += "_root_.xcala.play.extensions.MultilangTextHelper._"

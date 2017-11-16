import sbt._
import sbt.Keys._

organization := "com.ovoenergy"
scalaVersion := "2.11.11"

resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= Seq(
  ws,
  "com.ovoenergy" %% "comms-kafka-messages" % "1.36",
  "io.circe" %% "circe-core" % "0.7.0",
  "io.circe" %% "circe-generic-extras" % "0.7.0",
  "io.circe" %% "circe-parser" % "0.7.0",
  "io.circe" %% "circe-generic" % "0.7.0",
  "org.typelevel" %% "cats-core" % "0.9.0",
  "com.gu" %% "scanamo" % "0.9.1",
  "io.logz.logback" % "logzio-logback-appender" % "1.0.11",
  "me.moocar" % "logback-gelf" % "0.2",
  "com.gu" %% "play-googleauth" % "0.6.0",
  "com.ovoenergy" %% "comms-templates" % "0.12",
  "org.webjars" % "bootstrap" % "3.3.4",
  "com.squareup.okhttp3" % "okhttp" % "3.5.0",
  "com.sksamuel.scrimage" %% "scrimage-core" % "3.0.0-alpha4",
  "com.sksamuel.scrimage" %% "scrimage-io-extra" % "3.0.0-alpha4",
  "com.sksamuel.scrimage" %% "scrimage-filters" % "3.0.0-alpha4",
  "net.ruippeixotog" %% "scala-scraper" % "2.0.0",
  "org.scalatest" %% "scalatest" % "2.2.6" %  Test,
  "com.github.alexarchambault"  %% "scalacheck-shapeless_1.13" % "1.1.4" %   Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test
)

lazy val ipAddress: String = {
  val addr = "./get_ip_address.sh".!!.trim
  println(s"My IP address appears to be $addr")
  addr
}

val uploadAssetsToS3 = TaskKey[Unit]("uploadAssetsToS3", "upload shared templates assets to S3")

uploadAssetsToS3 := {
  import sys.process._
  println("Uploading template assets to s3...")
  "aws s3 sync assets/shared s3://ovo-comms-template-assets/shared".!!
  "aws s3 sync assets/shared s3://dev-ovo-comms-template-assets/shared".!!

}

val testReportsDir = sys.env.getOrElse("CI_REPORTS", "target/reports")
testOptions += Tests.Argument(TestFrameworks.ScalaTest, "-oF", "-u", testReportsDir, "-l", "DockerComposeTag")
test in Test := (test in Test).dependsOn(startDynamoDBLocal).value
testOptions in Test += dynamoDBLocalTestCleanup.value
testTagsToExecute := "DockerComposeTag"
dockerImageCreationTask := (publishLocal in Docker).value
credstashInputDir := file("conf")
enablePlugins(PlayScala, DockerPlugin, DockerComposePlugin)
variablesForSubstitution := Map("IP_ADDRESS" -> ipAddress)

val testWithDynamo = taskKey[Unit]("start dynamo, run the tests, shut down dynamo")
testWithDynamo := Def.sequential(
  startDynamoDBLocal,
  test in Test,
  stopDynamoDBLocal
).value

includeFilter in (Assets, LessKeys.less) := "*.less"

val scalafmtAll = taskKey[Unit]("Run scalafmt in non-interactive mode with no arguments")
scalafmtAll := {
  import org.scalafmt.bootstrap.ScalafmtBootstrap
  streams.value.log.info("Running scalafmt ...")
  ScalafmtBootstrap.main(Seq("--non-interactive"))
  streams.value.log.info("Done")
}
(compile in Compile) := (compile in Compile).dependsOn(scalafmtAll).value

commsPackagingHeapSize := 512
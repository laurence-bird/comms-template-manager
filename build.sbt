import sbt._
import sbt.Keys._
import Dependencies._

organization := "com.ovoenergy"
scalaVersion := "2.12.4"

resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= Seq(
  ws,
  OvoEnergy.commsKafkaMessages,
  OvoEnergy.commsTemplates,
  Circe.core,
  Circe.shapes,
  Circe.genericExtras,
  Circe.parser,
  Circe.generic,
  Guardian.scanamo,
  Guardian.playGoogleauth,
  SkSamuel.core,
  SkSamuel.ioExtra,
  SkSamuel.filters,
  catsCore,
  logzIoLogback,
  okHttp,
  scalaScraper,
  enumeratum,
  logbackGelf,
  bootstarp,
  scalaTest,
  scalaCheck,
  mockserverClientJava,
  scalaCheckShapeless
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
scalacOptions += "-Ypartial-unification"
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
commsPackagingMaxMetaspaceSize := 256
organization := "com.ovoenergy"
scalaVersion := "2.11.8"

resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= Seq(
  ws,
  "com.ovoenergy" %% "comms-kafka-messages" % "1.0",
  "io.circe" %% "circe-generic" % "0.7.0",
  "io.circe" %% "circe-core" % "0.7.0",
  "io.circe" %% "circe-generic-extras" % "0.7.0",
  "io.circe" %% "circe-parser" % "0.7.0",
  "io.circe" %% "circe-generic" % "0.7.0",
  "org.typelevel" %% "cats-core" % "0.9.0",
  "com.gu" %% "scanamo" % "0.9.1",
  "io.logz.logback" % "logzio-logback-appender" % "1.0.11",
  "me.moocar" % "logback-gelf" % "0.2",
  "com.gu" %% "play-googleauth" % "0.6.0",
  "com.ovoenergy" %% "comms-templates" % "0.1.1",
  "org.webjars" % "bootstrap" % "3.3.4",
  "com.squareup.okhttp3" % "okhttp" % "3.5.0",
  "org.scalatest" %% "scalatest" % "2.2.6" %  Test,
  "com.github.alexarchambault"  %% "scalacheck-shapeless_1.13" % "1.1.4" %   Test,
  "org.scalacheck" %% "scalacheck" % "1.13.4" % Test,
  "com.squareup.okhttp3" % "okhttp" % "3.4.2" % Test
)

lazy val ipAddress: String = {
  val addr = "./get_ip_address.sh".!!.trim
  println(s"My IP address appears to be $addr")
  addr
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

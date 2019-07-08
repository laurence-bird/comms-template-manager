import sbt._

object Dependencies {

  object OvoEnergy {
    val commsKafkaMessages = "com.ovoenergy" %% "comms-kafka-messages" % "1.68"
    val commsTemplates     = "com.ovoenergy" %% "comms-templates"      % "0.35"
  }

  object Circe {
    private val circeVersion = "0.9.1"
    val core                 = "io.circe" %% "circe-core" % circeVersion
    val shapes               = "io.circe" %% "circe-shapes" % circeVersion
    val genericExtras        = "io.circe" %% "circe-generic-extras" % circeVersion
    val parser               = "io.circe" %% "circe-parser" % circeVersion
    val generic              = "io.circe" %% "circe-generic" % circeVersion
  }

  object Guardian {
    val scanamo        = "com.gu" %% "scanamo"         % "1.0.0-M3"
    val playGoogleauth = "com.gu" %% "play-googleauth" % "0.7.2"
  }

  object SkSamuel {
    private val scrimageVersion = "3.0.0-alpha4"
    val core                    = "com.sksamuel.scrimage" %% "scrimage-core" % scrimageVersion
    val ioExtra                 = "com.sksamuel.scrimage" %% "scrimage-io-extra" % scrimageVersion
    val filters                 = "com.sksamuel.scrimage" %% "scrimage-filters" % scrimageVersion
  }

  val catsCore      = "org.typelevel"        %% "cats-core"              % "1.0.0"
  val logzIoLogback = "io.logz.logback"      % "logzio-logback-appender" % "1.0.11"
  val okHttp        = "com.squareup.okhttp3" % "okhttp"                  % "3.4.2"
  val scalaScraper  = "net.ruippeixotog"     %% "scala-scraper"          % "2.0.0"
  val enumeratum    = "com.beachape"         %% "enumeratum"             % "1.5.13"
  val logbackGelf   = "me.moocar"            % "logback-gelf"            % "0.2"
  val bootstarp     = "org.webjars"          % "bootstrap"               % "3.3.4"

  val scalaTest            = "org.scalatest"              %% "scalatest"                 % "3.0.3"  % Test
  val scalaCheck           = "org.scalacheck"             %% "scalacheck"                % "1.13.4" % Test
  val mockserverClientJava = "org.mock-server"            % "mockserver-client-java"     % "3.12"   % Test
  val scalaCheckShapeless  = "com.github.alexarchambault" %% "scalacheck-shapeless_1.13" % "1.1.4"  % Test
}
